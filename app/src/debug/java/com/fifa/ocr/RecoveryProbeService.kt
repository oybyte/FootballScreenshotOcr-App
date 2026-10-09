package com.fifa.ocr

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.runBlocking

/** Debug-only process-kill probe for validating P2 reconciliation after each file stage. */
class RecoveryProbeService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "P2 recovery probe", NotificationManager.IMPORTANCE_LOW),
            )
        }
        startForeground(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("P2 recovery probe")
                .setContentText("Debug-only process recovery test")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build(),
        )
        val point = intent?.getStringExtra(EXTRA_FAILURE_POINT)?.let { name ->
            runCatching { FailurePoint.valueOf(name) }.getOrNull()
        } ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        Log.i(TAG, "starting recovery probe at $point")
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
                    val taskId = store.createTask()
                    val sessionId = store.createSession(taskId, SlotKind.ASIAN_HANDICAP, CaptureMode.MANUAL, CaptureProvider.MANUAL_IMPORT)
                    val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff336699.toInt()) }
                    Log.i(TAG, "persisting probe frame")
                    store.persistFrame(sessionId, 1, PersistableFrame(bitmap, CaptureProvider.MANUAL_IMPORT, Instant.now(), 12, 12))
                }
            }.onFailure { stopSelf(startId) }
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_FAILURE_POINT = "failure_point"
        private const val CHANNEL_ID = "p2_recovery_probe"
        private const val NOTIFICATION_ID = 9904
        private const val TAG = "RecoveryProbe"
    }
}
