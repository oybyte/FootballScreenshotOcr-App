package com.fifa.ocr.data.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fifa.ocr.core.contract.CaptureMode
import com.fifa.ocr.core.contract.CaptureProvider
import com.fifa.ocr.core.contract.CaptureSessionStatus
import com.fifa.ocr.core.contract.SlotKind
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureStoreInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var fileStore: AndroidCaptureFileStore
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "captures").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fileStore = AndroidCaptureFileStore(context)
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "captures").deleteRecursively()
    }

    @Test
    fun bitmapIsCommittedOutsideRoomAndRevisionIncrements() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskId = store.createTask()
        val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val result = store.persistFrame(sessionId, 1, frame())

        val asset = database.assetDao().find(result.assetId)!!
        assertEquals("COMMITTED", asset.writeState)
        assertTrue(File(context.filesDir, "captures/${asset.relativePath}").isFile)
        assertEquals(1, database.taskDao().find(taskId)!!.revision)
        assertTrue(database.assetDao().findAll().all { it.fileSize > 0L })
    }

    @Test
    fun sameTaskHashIsReusedAndDifferentTaskIsIsolated() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskOne = store.createTask()
        val sessionOne = store.createSession(taskOne, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val first = store.persistFrame(sessionOne, 1, frame())
        val sessionTwo = store.createSession(taskOne, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val duplicate = store.persistFrame(sessionTwo, 2, frame())
        assertEquals(first.assetId, duplicate.assetId)
        assertEquals(1, database.assetDao().findAll().size)

        val taskTwo = store.createTask()
        val sessionThree = store.createSession(taskTwo, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val isolated = store.persistFrame(sessionThree, 1, frame())
        assertFalse(first.assetId == isolated.assetId)
    }

    @Test
    fun renameFailurePointLeavesFileForReconciliation() = runBlocking {
        val fired = AtomicBoolean(false)
        val store = RoomCaptureStore(database, fileStore, FailureInjector {
            if (it == FailurePoint.AFTER_RENAME_BEFORE_DB_COMMIT && fired.compareAndSet(false, true)) error("injected")
        })
        val taskId = store.createTask()
        val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val failed = runCatching { store.persistFrame(sessionId, 1, frame()) }
        assertTrue(failed.isFailure)
        val report = store.reconcile()
        assertTrue(report.committedAssets >= 1)
        assertEquals("COMMITTED", database.assetDao().findAll().single().writeState)
    }

    @Test
    fun reconciliationRestoresCommitMetadataForPreCommitFailuresAndIsIdempotent() = runBlocking {
        for (point in listOf(FailurePoint.AFTER_TEMP_WRITE, FailurePoint.AFTER_VALIDATION, FailurePoint.AFTER_RENAME_BEFORE_DB_COMMIT)) {
            val fired = AtomicBoolean(false)
            val store = RoomCaptureStore(database, fileStore, FailureInjector {
                if (it == point && fired.compareAndSet(false, true)) error("injected $point")
            })
            val taskId = store.createTask()
            val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
            assertTrue(runCatching { store.persistFrame(sessionId, 1, frame()) }.isFailure)

            val first = store.reconcile()
            assertEquals(1, first.committedAssets)
            assertEquals("COMMITTED", database.assetDao().findAll().single { it.taskId == taskId }.writeState)
            assertEquals("COMMITTED", database.segmentDao().find(sessionId, 1)!!.state)
            assertEquals("IMAGES_READY", database.slotDao().find(taskId, SlotKind.ASIAN_HANDICAP.name)!!.state)
            assertEquals(CaptureProvider.MANUAL_IMPORT.name, database.slotDao().find(taskId, SlotKind.ASIAN_HANDICAP.name)!!.captureSource)
            assertEquals(CaptureSessionStatus.SEGMENT_COMMITTED.name, database.sessionDao().find(sessionId)!!.status)
            assertEquals(1, database.taskDao().find(taskId)!!.revision)
            assertEquals(1, database.revisionDao().findForTask(taskId).count { it.operation == "ASSET_COMMITTED" })

            val second = store.reconcile()
            assertEquals(0, second.committedAssets)
            assertEquals(1, database.taskDao().find(taskId)!!.revision)
            assertEquals(1, database.revisionDao().findForTask(taskId).count { it.operation == "ASSET_COMMITTED" })
        }
    }

    @Test
    fun orphanPendingFileIsReportedKeptAndDeduplicated() = runBlocking {
        val orphan = fileStore.writeBitmapTemp("orphan", frame().bitmap)
        val store = RoomCaptureStore(database, fileStore)

        val first = store.reconcile()
        val second = store.reconcile()

        assertEquals(1, first.orphanPendingFiles)
        assertEquals(1, second.orphanPendingFiles)
        assertTrue(orphan.file.isFile)
        assertEquals(1, database.diagnosticDao().countByCode("ORPHAN_PENDING_FILE"))
    }

    @Test
    fun processStartupMarksActiveSessionInterruptedAndResumeWaitsForUser() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskId = store.createTask()
        val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        store.interruptActiveSessionsOnStartup()
        assertEquals(CaptureSessionStatus.INTERRUPTED.name, database.sessionDao().find(sessionId)!!.status)
        store.resumeSession(sessionId)
        assertEquals(CaptureSessionStatus.WAITING_USER.name, database.sessionDao().find(sessionId)!!.status)
    }

    @Test
    fun pauseResumeKeepsPreviousStatusAndReplayEvents() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskId = store.createTask()
        val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        database.sessionDao().setStatus(sessionId, CaptureSessionStatus.CAPTURING.name, Instant.EPOCH.toString())
        store.pauseSession(sessionId, "user paused")
        assertEquals(CaptureSessionStatus.PAUSED.name, database.sessionDao().find(sessionId)!!.status)
        store.resumeSession(sessionId)
        val resumed = database.sessionDao().find(sessionId)!!
        assertEquals(CaptureSessionStatus.CAPTURING.name, resumed.status)
        assertTrue(resumed.replayJson.contains("paused"))
        assertTrue(resumed.replayJson.contains("resumed"))
    }

    @Test
    fun missingAssetIsRecoveryRequiredAndReconciliationIsIdempotent() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskId = store.createTask()
        val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val result = store.persistFrame(sessionId, 1, frame())
        File(context.filesDir, "captures/${result.relativePath}").delete()
        val first = store.reconcile()
        val second = store.reconcile()
        assertTrue(first.missingFiles >= 1)
        assertEquals(0, second.committedAssets)
        assertEquals("RECOVERY_REQUIRED", database.assetDao().find(result.assetId)!!.writeState)
    }

    @Test
    fun everyInjectedStageLeavesARecoverableOrCommittedRecord() = runBlocking {
        for (point in FailurePoint.entries) {
            val fired = AtomicBoolean(false)
            val store = RoomCaptureStore(database, fileStore, FailureInjector {
                if (it == point && fired.compareAndSet(false, true)) error("injected $point")
            })
            val taskId = store.createTask()
            val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
            runCatching { store.persistFrame(sessionId, 1, frame()) }
            store.reconcile()
            val states = database.assetDao().findAll().filter { it.taskId == taskId }.map { it.writeState }
            assertTrue("no asset for $point", states.isNotEmpty())
            assertTrue("unexpected state for $point: $states", states.all { it == "COMMITTED" || it == "RECOVERY_REQUIRED" })
        }
    }

    @Test
    fun segmentCannotJoinSessionAndAssetFromDifferentTasks() = runBlocking {
        val store = RoomCaptureStore(database, fileStore)
        val taskOne = store.createTask()
        val taskTwo = store.createTask()
        val sessionTwo = store.createSession(taskTwo, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
        val assetId = "asset-one"
        database.assetDao().insert(
            AssetEntity(assetId, taskOne, SlotKind.ASIAN_HANDICAP.name, 1, "abc", "committed/$taskOne/$assetId.png", 1, 1, 1, Instant.EPOCH.toString(), "COMMITTED"),
        )
        val failure = runCatching {
            database.segmentDao().insert(
                CaptureSegmentEntity("segment-two", taskTwo, sessionTwo, assetId, 1, Instant.EPOCH.toString(), "PENDING"),
            )
        }.exceptionOrNull()
        assertTrue(failure is SQLiteConstraintException)
    }

    private fun frame(): PersistableFrame {
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff336699.toInt())
        return PersistableFrame(bitmap, CaptureProvider.MANUAL_IMPORT, Instant.EPOCH, 12, 12)
    }
}
