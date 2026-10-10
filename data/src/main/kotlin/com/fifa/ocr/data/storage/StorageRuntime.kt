package com.fifa.ocr.data.storage

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

enum class StorageRuntimeState {
    UNINITIALIZED,
    RECONCILING,
    READY,
    FAILED,
}

internal class ReconciliationReadiness {
    private val mutableState = MutableStateFlow(StorageRuntimeState.UNINITIALIZED)
    val state: StateFlow<StorageRuntimeState> = mutableState
    @Volatile private var failure: Throwable? = null

    fun start(scope: CoroutineScope, reconcile: suspend () -> Unit) {
        synchronized(this) {
            if (mutableState.value != StorageRuntimeState.UNINITIALIZED) return
            mutableState.value = StorageRuntimeState.RECONCILING
        }
        scope.launch {
            try {
                reconcile()
                mutableState.value = StorageRuntimeState.READY
            } catch (exception: CancellationException) {
                failure = exception
                mutableState.value = StorageRuntimeState.FAILED
                throw exception
            } catch (exception: Exception) {
                failure = exception
                mutableState.value = StorageRuntimeState.FAILED
            }
        }
    }

    suspend fun awaitReady() {
        val completed = state.first { it == StorageRuntimeState.READY || it == StorageRuntimeState.FAILED }
        if (completed == StorageRuntimeState.FAILED) {
            throw IllegalStateException("Storage reconciliation failed", failure)
        }
    }
}

/** Process-level owner for P2 storage; UI code receives the CaptureStore boundary. */
object StorageRuntime {
    private val readiness = ReconciliationReadiness()
    val state: StateFlow<StorageRuntimeState> = readiness.state

    lateinit var database: AppDatabase
        private set

    lateinit var captureStore: CaptureStore
        private set

    suspend fun awaitReady() = readiness.awaitReady()

    fun initialize(context: Context) {
        synchronized(this) {
            if (state.value != StorageRuntimeState.UNINITIALIZED) return
            val appContext = context.applicationContext
            database = Room.databaseBuilder(appContext, AppDatabase::class.java, "football_screenshot_ocr.db").build()
            captureStore = RoomCaptureStore(database, AndroidCaptureFileStore(appContext))
            readiness.start(CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                captureStore.interruptActiveSessionsOnStartup()
                captureStore.reconcile()
            }
        }
    }
}
