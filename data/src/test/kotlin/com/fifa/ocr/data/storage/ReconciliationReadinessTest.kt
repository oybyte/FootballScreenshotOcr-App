package com.fifa.ocr.data.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationReadinessTest {
    @Test
    fun readinessWaitsUntilReconciliationCompletes() = runBlocking {
        val readiness = ReconciliationReadiness()
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        readiness.start(scope) { gate.await() }
        assertEquals(StorageRuntimeState.RECONCILING, readiness.state.value)
        val waiter = async { readiness.awaitReady() }
        assertFalse(waiter.isCompleted)

        gate.complete(Unit)
        waiter.await()
        assertEquals(StorageRuntimeState.READY, readiness.state.value)
        scope.cancel()
    }

    @Test
    fun reconciliationFailureIsObservableToWaiters() = runBlocking {
        val readiness = ReconciliationReadiness()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        readiness.start(scope) { error("reconcile failed") }
        val failure = runCatching { readiness.awaitReady() }.exceptionOrNull()

        assertEquals(StorageRuntimeState.FAILED, readiness.state.value)
        assertTrue(failure is IllegalStateException)
        assertEquals("reconcile failed", failure?.cause?.message)
    }
}
