package com.fifa.ocr

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.fifa.ocr.core.contract.CaptureMode
import com.fifa.ocr.core.contract.CaptureProvider
import com.fifa.ocr.core.contract.SlotKind
import com.fifa.ocr.data.storage.AndroidCaptureFileStore
import com.fifa.ocr.data.storage.CaptureStore
import com.fifa.ocr.data.storage.FailureInjector
import com.fifa.ocr.data.storage.FailurePoint
import com.fifa.ocr.data.storage.PersistableFrame
import com.fifa.ocr.data.storage.RoomCaptureStore
import com.fifa.ocr.data.storage.StorageRuntime
import java.time.Instant
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import kotlinx.coroutines.runBlocking

/** Debug-only process-kill probe for validating P2 reconciliation after each file stage. */
class RecoveryProbeService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_VERIFY, false) == true) {
            Thread { verifyAfterRestart(startId) }.start()
            return START_NOT_STICKY
        }
        val point = intent?.getStringExtra(EXTRA_FAILURE_POINT)?.let { name ->
            runCatching { FailurePoint.valueOf(name) }.getOrNull()
        } ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        Thread {
            val store: CaptureStore = RoomCaptureStore(
                StorageRuntime.database,
                AndroidCaptureFileStore(applicationContext),
                FailureInjector { observed ->
                    Log.i(TAG, "failure point observed: $observed")
                    if (observed == point) Process.killProcess(Process.myPid())
                },
            )
            runCatching {
                runBlocking {
                    StorageRuntime.awaitReady()
                    val taskId = store.createTask()
                    val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
                    writeProbeProperties(
                        Properties().apply {
                            setProperty("taskId", taskId)
                            setProperty("sessionId", sessionId)
                            setProperty("failurePoint", point.name)
                            setProperty("verified", "pending")
                        },
                    )
                    Log.i(TAG, "starting recovery probe task=$taskId session=$sessionId at $point")
                    val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff336699.toInt()) }
                    Log.i(TAG, "persisting probe frame")
                    store.persistFrame(sessionId, 1, PersistableFrame(bitmap, CaptureProvider.MANUAL_IMPORT, Instant.now(), 12, 12))
                }
            }.onFailure { stopSelf(startId) }
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun verifyAfterRestart(startId: Int) {
        runCatching {
            runBlocking {
                StorageRuntime.awaitReady()
                val properties = readProbeProperties()
                val taskId = requireNotNull(properties.getProperty("taskId"))
                val sessionId = requireNotNull(properties.getProperty("sessionId"))
                val point = FailurePoint.valueOf(requireNotNull(properties.getProperty("failurePoint")))
                val database = StorageRuntime.database
                val segment = requireNotNull(database.segmentDao().find(sessionId, 1))
                val asset = requireNotNull(database.assetDao().find(segment.assetId))
                val task = requireNotNull(database.taskDao().find(taskId))
                val slot = requireNotNull(database.slotDao().find(taskId, SlotKind.ASIAN_HANDICAP.name))
                val session = requireNotNull(database.sessionDao().find(sessionId))
                val revisions = database.revisionDao().findForTask(taskId).count { it.operation == "ASSET_COMMITTED" }
                val expectedCommitted = point != FailurePoint.AFTER_PENDING_INSERT
                check(if (expectedCommitted) asset.writeState == "COMMITTED" else asset.writeState == "RECOVERY_REQUIRED")
                check(if (expectedCommitted) segment.state == "COMMITTED" else segment.state == "PENDING")
                check(if (expectedCommitted) slot.state == "IMAGES_READY" && slot.revision == 1 else slot.state == "EMPTY" && slot.revision == 0)
                check(task.revision == if (expectedCommitted) 1 else 0)
                check(revisions == if (expectedCommitted) 1 else 0)
                val expectedSessionStatus = when (point) {
                    FailurePoint.AFTER_PENDING_INSERT,
                    FailurePoint.AFTER_DB_COMMIT -> "INTERRUPTED"
                    else -> "SEGMENT_COMMITTED"
                }
                check(session.status == expectedSessionStatus)
                val file = File(filesDir, "captures/${asset.relativePath}")
                check(file.isFile == expectedCommitted)

                val before = Triple(task.revision, slot.revision, session.revision)
                StorageRuntime.captureStore.reconcile()
                val afterTask = requireNotNull(database.taskDao().find(taskId))
                val afterSlot = requireNotNull(database.slotDao().find(taskId, SlotKind.ASIAN_HANDICAP.name))
                val afterSession = requireNotNull(database.sessionDao().find(sessionId))
                val afterRevisions = database.revisionDao().findForTask(taskId).count { it.operation == "ASSET_COMMITTED" }
                check(before == Triple(afterTask.revision, afterSlot.revision, afterSession.revision))
                check(afterRevisions == revisions)
                properties.setProperty("verified", "true")
                properties.setProperty("taskRevision", afterTask.revision.toString())
                properties.setProperty("slotRevision", afterSlot.revision.toString())
                properties.setProperty("sessionRevision", afterSession.revision.toString())
                properties.setProperty("assetState", asset.writeState)
                properties.setProperty("segmentState", segment.state)
                properties.setProperty("revisionCount", afterRevisions.toString())
                writeProbeProperties(properties)
                Log.i(TAG, "recovery verified task=$taskId session=$sessionId point=$point")
            }
        }.onFailure { exception ->
            runCatching {
                val properties = readProbeProperties()
                properties.setProperty("verified", "false")
                properties.setProperty("verificationError", exception.stackTraceToString())
                writeProbeProperties(properties)
            }
            Log.e(TAG, "recovery verification failed", exception)
        }
        stopSelf(startId)
    }

    private fun readProbeProperties(): Properties = Properties().also { properties ->
        File(filesDir, PROBE_FILE).inputStream().use(properties::load)
    }

    private fun writeProbeProperties(properties: Properties) {
        FileOutputStream(File(filesDir, PROBE_FILE)).use { output ->
            properties.store(output, "debug process recovery probe")
            output.fd.sync()
        }
    }

    companion object {
        const val EXTRA_FAILURE_POINT = "failure_point"
        const val EXTRA_VERIFY = "verify"
        const val PROBE_FILE = "recovery-probe.properties"
        private const val TAG = "RecoveryProbe"
    }
}
