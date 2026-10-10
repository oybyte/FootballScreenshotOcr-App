package com.fifa.ocr.data.storage

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.fifa.ocr.core.contract.CaptureMode
import com.fifa.ocr.core.contract.CaptureProvider
import com.fifa.ocr.core.contract.CaptureSessionStatus
import com.fifa.ocr.core.contract.CoverageState
import com.fifa.ocr.core.contract.ReplayEvent
import com.fifa.ocr.core.contract.ReplayEventType
import com.fifa.ocr.core.contract.ReplayLog
import com.fifa.ocr.core.contract.SlotKind
import com.fifa.ocr.core.contract.SlotState
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

data class PersistableFrame(
    val bitmap: Bitmap,
    val source: CaptureProvider,
    val capturedAt: Instant,
    val displayWidth: Int,
    val displayHeight: Int,
)

data class PersistedSegment(
    val assetId: String,
    val segmentId: String,
    val sha256: String,
    val relativePath: String,
    val state: String,
)

data class SessionRecoveryItem(
    val sessionId: String,
    val taskId: String,
    val slot: SlotKind,
    val status: CaptureSessionStatus,
)

data class ReconciliationReport(
    val committedAssets: Int,
    val recoveryRequiredAssets: Int,
    val orphanFiles: Int,
    val orphanPendingFiles: Int,
    val missingFiles: Int,
    val interruptedSessions: Int,
)

enum class FailurePoint {
    AFTER_PENDING_INSERT,
    AFTER_TEMP_WRITE,
    AFTER_VALIDATION,
    AFTER_RENAME_BEFORE_DB_COMMIT,
    AFTER_DB_COMMIT,
}

fun interface FailureInjector {
    fun check(point: FailurePoint)
}

interface CaptureStore {
    suspend fun createTask(): String

    suspend fun createSession(taskId: String, slot: SlotKind, mode: CaptureMode, provider: CaptureProvider): String

    suspend fun persistFrame(sessionId: String, sequence: Int, frame: PersistableFrame): PersistedSegment

    suspend fun pauseSession(sessionId: String, reason: String?)
    suspend fun resumeSession(sessionId: String)
    suspend fun interruptActiveSessionsOnStartup()
    suspend fun reconcile(): ReconciliationReport
    suspend fun getRecoverableSessions(): List<SessionRecoveryItem>
}

class RoomCaptureStore(
    private val database: AppDatabase,
    private val fileStore: CaptureFileStore,
    private val failureInjector: FailureInjector = FailureInjector { },
    private val clock: () -> Instant = Instant::now,
    private val idGenerator: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) : CaptureStore {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    override suspend fun createTask(): String {
        val taskId = idGenerator()
        val now = clock().toString()
        database.withTransaction {
            database.taskDao().insert(TaskEntity(taskId, "DRAFT", now, now, 0, "NOT_READY"))
            database.slotDao().insertAll(SlotKind.entries.map { slot ->
                SlotEntity(taskId, slot.name, SlotState.EMPTY.name, CoverageState.NOT_RECORDED.name)
            })
            database.fixtureDao().insert(FixtureEntity(taskId))
        }
        return taskId
    }

    override suspend fun createSession(taskId: String, slot: SlotKind, mode: CaptureMode, provider: CaptureProvider): String {
        require(database.taskDao().find(taskId) != null) { "Unknown task: $taskId" }
        require(database.slotDao().find(taskId, slot.name) != null) { "Unknown slot: ${slot.name}" }
        val sessionId = idGenerator()
        val now = clock().toString()
        database.sessionDao().insert(
            CaptureSessionEntity(
                sessionId = sessionId,
                taskId = taskId,
                slotKind = slot.name,
                mode = mode.name,
                provider = provider.name,
                status = CaptureSessionStatus.PREPARING.name,
                startedAt = now,
                updatedAt = now,
                coverage = CoverageState.NOT_RECORDED.name,
            ),
        )
        return sessionId
    }

    override suspend fun persistFrame(sessionId: String, sequence: Int, frame: PersistableFrame): PersistedSegment {
        require(sequence > 0)
        val session = requireNotNull(database.sessionDao().find(sessionId)) { "Unknown session: $sessionId" }
        require(CaptureSessionStatus.valueOf(session.status) !in terminalStatuses) { "Session is terminal" }
        val assetId = idGenerator()
        val segmentId = idGenerator()
        val relativePath = "committed/${session.taskId}/$assetId.png"
        val now = frame.capturedAt.toString()
        database.withTransaction {
            database.assetDao().insert(
                AssetEntity(assetId, session.taskId, session.slotKind, sequence, null, relativePath, 0, 0, 0, now, "PENDING"),
            )
            database.segmentDao().insert(
                CaptureSegmentEntity(segmentId, session.taskId, sessionId, assetId, sequence, now, "PENDING"),
            )
        }
        failureInjector.check(FailurePoint.AFTER_PENDING_INSERT)

        val storageHealth = try {
            fileStore.storageHealth()
        } catch (exception: Exception) {
            database.assetDao().updateState(assetId, "RECOVERY_REQUIRED")
            val code = if (exception.isNoSpace()) "STORAGE_LOW" else "STORAGE_HEALTH_UNAVAILABLE"
            addDiagnostic(session.taskId, sessionId, null, assetId, code, exception.message ?: "storage health query failed")
            throw exception
        }
        val requiredBytes = frame.bitmap.allocationByteCount.toLong()
        if (!storageHealth.hasCapacity(requiredBytes)) {
            val exception = StorageLowException(
                "Insufficient storage: available=${storageHealth.availableBytes}, reserve=${storageHealth.systemReserveBytes}, required=$requiredBytes",
            )
            database.assetDao().updateState(assetId, "RECOVERY_REQUIRED")
            addDiagnostic(session.taskId, sessionId, null, assetId, "STORAGE_LOW", exception.message ?: "storage is below the safe threshold")
            throw exception
        }

        val temp = try {
            fileStore.writeBitmapTemp(assetId, frame.bitmap)
        } catch (exception: Exception) {
            database.assetDao().updateState(assetId, "RECOVERY_REQUIRED")
            val code = if (exception.isNoSpace()) "STORAGE_LOW" else "FILE_WRITE_FAILED"
            addDiagnostic(session.taskId, sessionId, null, assetId, code, exception.message ?: "file write failed")
            throw exception
        }
        failureInjector.check(FailurePoint.AFTER_TEMP_WRITE)

        val validated = try {
            fileStore.validate(temp)
        } catch (exception: Exception) {
            database.assetDao().updateState(assetId, "RECOVERY_REQUIRED")
            addDiagnostic(session.taskId, sessionId, segmentId, assetId, "ASSET_VALIDATION_FAILED", exception.message ?: "asset validation failed")
            throw exception
        }
        database.assetDao().updateValidated(assetId, "VALIDATED", validated.sha256, validated.fileSize, validated.width, validated.height)
        failureInjector.check(FailurePoint.AFTER_VALIDATION)

        val duplicate = database.assetDao().findCommittedByHash(session.taskId, validated.sha256)
        if (duplicate != null) {
            fileStore.discardSuccessfulDuplicate(temp)
            database.withTransaction {
                database.segmentDao().updateAssetAndState(segmentId, duplicate.assetId, "COMMITTED")
                database.assetDao().delete(assetId)
                database.slotDao().markImagesReady(session.taskId, session.slotKind, SlotState.IMAGES_READY.name, frame.source.name)
                database.sessionDao().markSegmentCommitted(sessionId, clock().toString())
                database.taskDao().incrementRevision(session.taskId, clock().toString())
            }
            return PersistedSegment(duplicate.assetId, segmentId, validated.sha256, duplicate.relativePath, "COMMITTED")
        }

        fileStore.commit(temp, relativePath)
        failureInjector.check(FailurePoint.AFTER_RENAME_BEFORE_DB_COMMIT)
        database.withTransaction {
            val nextRevision = (database.taskDao().find(session.taskId)?.revision ?: 0) + 1
            database.assetDao().updateState(assetId, "COMMITTED")
            database.segmentDao().updateAssetAndState(segmentId, assetId, "COMMITTED")
            database.slotDao().markImagesReady(session.taskId, session.slotKind, SlotState.IMAGES_READY.name, frame.source.name)
            database.sessionDao().markSegmentCommitted(sessionId, clock().toString())
            database.taskDao().incrementRevision(session.taskId, clock().toString())
            database.revisionDao().insert(RevisionEntity(session.taskId, nextRevision, "ASSET_COMMITTED", clock().toString(), "capture", validated.sha256))
        }
        failureInjector.check(FailurePoint.AFTER_DB_COMMIT)
        return PersistedSegment(assetId, segmentId, validated.sha256, relativePath, "COMMITTED")
    }

    override suspend fun pauseSession(sessionId: String, reason: String?) {
        val session = requireNotNull(database.sessionDao().find(sessionId))
        val status = CaptureSessionStatus.valueOf(session.status)
        require(status !in terminalStatuses) { "Terminal session cannot be paused" }
        val replay = appendReplay(session.replayJson, ReplayEventType.paused, reason)
        database.sessionDao().updateStatusWithPausedFrom(sessionId, CaptureSessionStatus.PAUSED.name, clock().toString(), replay, status.name)
    }

    override suspend fun resumeSession(sessionId: String) {
        val session = requireNotNull(database.sessionDao().find(sessionId))
        require(session.status == CaptureSessionStatus.PAUSED.name || session.status == CaptureSessionStatus.INTERRUPTED.name) { "Session is not recoverable" }
        val previous = session.pausedFromStatus?.let { runCatching { CaptureSessionStatus.valueOf(it) }.getOrNull() }
        val target = previous?.takeUnless { it in terminalStatuses } ?: CaptureSessionStatus.WAITING_USER
        val replay = appendReplay(session.replayJson, ReplayEventType.resumed, null)
        database.sessionDao().updateStatusWithPausedFrom(sessionId, target.name, clock().toString(), replay, null)
    }

    override suspend fun interruptActiveSessionsOnStartup() {
        for (session in database.sessionDao().findNonTerminal()) {
            if (session.status == CaptureSessionStatus.INTERRUPTED.name) continue
            database.sessionDao().setStatus(session.sessionId, CaptureSessionStatus.INTERRUPTED.name, clock().toString())
            addDiagnostic(session.taskId, session.sessionId, null, null, "PROCESS_INTERRUPTED", "Capture session interrupted by process startup")
        }
    }

    override suspend fun reconcile(): ReconciliationReport {
        val files = fileStore.scan()
        var committed = 0
        var recovery = 0
        var missing = 0
        val pendingByAsset = database.assetDao().findPending().associateBy { it.assetId }
        for (asset in pendingByAsset.values) {
            val temp = files.pending.firstOrNull { it.name == "${asset.assetId}.tmp" }
            val finalFile = files.committed.firstOrNull { it.path.endsWith("${asset.assetId}.png") }
            try {
                when {
                    temp != null -> {
                        val validated = fileStore.validate(TempAsset(asset.assetId, temp))
                        require(asset.sha256 == null || asset.sha256 == validated.sha256) { "hash mismatch" }
                        fileStore.commit(TempAsset(asset.assetId, temp), asset.relativePath)
                        commitRecoveredAsset(asset, validated)
                        committed++
                    }
                    finalFile != null -> {
                        val validated = fileStore.validate(TempAsset(asset.assetId, finalFile))
                        require(asset.sha256 == null || asset.sha256 == validated.sha256) { "hash mismatch" }
                        commitRecoveredAsset(asset, validated)
                        committed++
                    }
                    else -> {
                        recovery++
                        missing++
                        database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                        addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_MISSING", "Committed or pending asset file is missing")
                    }
                }
            } catch (exception: RecoveredAssetRelationException) {
                recovery++
                database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_RELATION_MISSING", exception.message ?: "asset relation is incomplete")
            } catch (exception: Exception) {
                recovery++
                database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_HASH_MISMATCH", exception.message ?: "asset reconciliation failed")
            }
        }
        val knownPendingNames = pendingByAsset.keys.mapTo(mutableSetOf()) { "$it.tmp" }
        var orphanPendingFiles = 0
        for (file in files.pending) {
            if (file.name in knownPendingNames) continue
            orphanPendingFiles++
            addDiagnostic(null, null, null, null, "ORPHAN_PENDING_FILE", "Unreferenced pending file: ${file.name}")
        }
        val filesAfterRecovery = fileStore.scan()
        for (asset in database.assetDao().findCommitted()) {
            val finalFile = filesAfterRecovery.committed.firstOrNull { it.path.endsWith("${asset.assetId}.png") }
            if (finalFile == null) {
                recovery++
                missing++
                database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_MISSING", "Committed asset file is missing")
                continue
            }
            try {
                val validated = fileStore.validate(TempAsset(asset.assetId, finalFile))
                if (asset.sha256 != validated.sha256) {
                    recovery++
                    database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                    addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_HASH_MISMATCH", "Committed asset hash does not match Room")
                }
            } catch (exception: Exception) {
                recovery++
                database.assetDao().updateState(asset.assetId, "RECOVERY_REQUIRED")
                addDiagnostic(asset.taskId, null, null, asset.assetId, "ASSET_HASH_MISMATCH", exception.message ?: "asset validation failed")
            }
        }
        val committedAssets = database.assetDao().findCommitted()
        var orphanFiles = 0
        for (file in filesAfterRecovery.committed) {
            if (committedAssets.none { it.relativePath.endsWith("/${file.name}") }) {
                orphanFiles++
                addDiagnostic(null, null, null, null, "ORPHAN_FILE", "Unreferenced committed file: ${file.name}")
            }
        }
        return ReconciliationReport(
            committedAssets = committed,
            recoveryRequiredAssets = recovery,
            orphanFiles = orphanFiles,
            orphanPendingFiles = orphanPendingFiles,
            missingFiles = missing,
            interruptedSessions = database.sessionDao().findRecoverable().count { it.status == CaptureSessionStatus.INTERRUPTED.name },
        )
    }

    override suspend fun getRecoverableSessions(): List<SessionRecoveryItem> = database.sessionDao().findRecoverable().mapNotNull { session ->
        val slot = runCatching { SlotKind.valueOf(session.slotKind) }.getOrNull() ?: return@mapNotNull null
        SessionRecoveryItem(session.sessionId, session.taskId, slot, CaptureSessionStatus.valueOf(session.status))
    }

    private suspend fun addDiagnostic(taskId: String?, sessionId: String?, segmentId: String?, assetId: String?, code: String, message: String) {
        val key = listOf(code, taskId, sessionId, segmentId, assetId, message).joinToString("|")
        database.diagnosticDao().insert(DiagnosticEntity(idGenerator(), taskId, sessionId, segmentId, assetId, code, "ERROR", message, clock().toString(), key))
    }

    private suspend fun commitRecoveredAsset(asset: AssetEntity, validated: ValidatedAsset) {
        val segments = database.segmentDao().findForAsset(asset.taskId, asset.assetId)
        if (segments.size != 1) {
            throw RecoveredAssetRelationException("Expected one segment for pending asset ${asset.assetId}, found ${segments.size}")
        }
        val segment = segments.single()
        val session = database.sessionDao().find(segment.sessionId)
            ?: throw RecoveredAssetRelationException("Missing session ${segment.sessionId} for asset ${asset.assetId}")
        val committedAt = clock().toString()
        database.withTransaction {
            val nextRevision = (database.taskDao().find(asset.taskId)?.revision
                ?: throw RecoveredAssetRelationException("Missing task ${asset.taskId} for asset ${asset.assetId}")) + 1
            database.assetDao().updateValidated(asset.assetId, "COMMITTED", validated.sha256, validated.fileSize, validated.width, validated.height)
            database.segmentDao().markCommittedForAsset(asset.assetId)
            database.slotDao().markImagesReady(asset.taskId, asset.slotKind, SlotState.IMAGES_READY.name, session.provider)
            database.sessionDao().markSegmentCommitted(session.sessionId, committedAt)
            database.taskDao().incrementRevision(asset.taskId, committedAt)
            database.revisionDao().insert(RevisionEntity(asset.taskId, nextRevision, "ASSET_COMMITTED", committedAt, "capture", validated.sha256))
        }
    }

    private class RecoveredAssetRelationException(message: String) : IllegalStateException(message)

    private fun Throwable.isNoSpace(): Boolean = generateSequence(this) { it.cause }
        .any { cause ->
            val message = cause.message.orEmpty().lowercase()
            message.contains("enospc") || message.contains("no space left") || message.contains("not enough space")
        }

    private fun appendReplay(current: String, eventType: ReplayEventType, message: String?): String {
        val replay = runCatching { json.decodeFromString<ReplayLog>(current) }.getOrElse { ReplayLog(emptyList()) }
        val event = ReplayEvent((replay.events.maxOfOrNull { it.sequence } ?: 0) + 1, clock().toString(), eventType, message = message)
        return json.encodeToString(ReplayLog(replay.events + event))
    }

    companion object {
        private val terminalStatuses = setOf(CaptureSessionStatus.COMPLETED, CaptureSessionStatus.FAILED, CaptureSessionStatus.REVIEW_REQUIRED)
    }
}
