package com.fifa.ocr.data.storage

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-level owner for P2 storage; UI code receives the CaptureStore boundary. */
object StorageRuntime {
    @Volatile
    private var initialized = false

    lateinit var database: AppDatabase
        private set

    lateinit var captureStore: CaptureStore
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            database = Room.databaseBuilder(appContext, AppDatabase::class.java, "football_screenshot_ocr.db").build()
            captureStore = RoomCaptureStore(database, AndroidCaptureFileStore(appContext))
            initialized = true
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                captureStore.reconcile()
            }
        }
    }
}
