package com.fifa.ocr.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun find(taskId: String): TaskEntity?

    @Query("UPDATE tasks SET revision = revision + 1, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun incrementRevision(taskId: String, updatedAt: String)
}

@Dao
interface FixtureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fixture: FixtureEntity)
}

@Dao
interface SlotDao {
    @Insert
    suspend fun insertAll(slots: List<SlotEntity>)

    @Query("SELECT * FROM slots WHERE taskId = :taskId AND slotKind = :slotKind")
    suspend fun find(taskId: String, slotKind: String): SlotEntity?

    @Query("UPDATE slots SET revision = revision + 1, state = :state, captureSource = :captureSource WHERE taskId = :taskId AND slotKind = :slotKind")
    suspend fun markImagesReady(taskId: String, slotKind: String, state: String, captureSource: String)
}

@Dao
interface AssetDao {
    @Insert
    suspend fun insert(asset: AssetEntity)

    @Query("SELECT * FROM assets WHERE assetId = :assetId")
    suspend fun find(assetId: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE taskId = :taskId AND sha256 = :sha256 AND writeState = 'COMMITTED' LIMIT 1")
    suspend fun findCommittedByHash(taskId: String, sha256: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE writeState != 'COMMITTED'")
    suspend fun findPending(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE writeState = 'COMMITTED'")
    suspend fun findCommitted(): List<AssetEntity>

    @Query("SELECT * FROM assets")
    suspend fun findAll(): List<AssetEntity>

    @Query("UPDATE assets SET writeState = :state, sha256 = :sha256, fileSize = :fileSize, width = :width, height = :height WHERE assetId = :assetId")
    suspend fun updateValidated(assetId: String, state: String, sha256: String, fileSize: Long, width: Int, height: Int)

    @Query("UPDATE assets SET writeState = :state WHERE assetId = :assetId")
    suspend fun updateState(assetId: String, state: String)

    @Query("DELETE FROM assets WHERE assetId = :assetId")
    suspend fun delete(assetId: String)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: CaptureSessionEntity)

    @Query("SELECT * FROM capture_sessions WHERE sessionId = :sessionId")
    suspend fun find(sessionId: String): CaptureSessionEntity?

    @Query("SELECT * FROM capture_sessions WHERE status IN ('INTERRUPTED', 'PAUSED')")
    suspend fun findRecoverable(): List<CaptureSessionEntity>

    @Query("SELECT * FROM capture_sessions WHERE status NOT IN ('COMPLETED', 'FAILED', 'REVIEW_REQUIRED', 'PAUSED', 'INTERRUPTED')")
    suspend fun findNonTerminal(): List<CaptureSessionEntity>

    @Query("UPDATE capture_sessions SET status = :status, updatedAt = :updatedAt, replayJson = :replayJson, revision = revision + 1, pausedFromStatus = NULL WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, updatedAt: String, replayJson: String)

    @Query("UPDATE capture_sessions SET status = :status, updatedAt = :updatedAt, replayJson = :replayJson, revision = revision + 1, pausedFromStatus = :pausedFromStatus WHERE sessionId = :sessionId")
    suspend fun updateStatusWithPausedFrom(sessionId: String, status: String, updatedAt: String, replayJson: String, pausedFromStatus: String?)

    @Query("UPDATE capture_sessions SET status = :status, updatedAt = :updatedAt, revision = revision + 1 WHERE sessionId = :sessionId")
    suspend fun setStatus(sessionId: String, status: String, updatedAt: String)

    @Query("UPDATE capture_sessions SET status = 'SEGMENT_COMMITTED', updatedAt = :updatedAt, revision = revision + 1 WHERE sessionId = :sessionId")
    suspend fun markSegmentCommitted(sessionId: String, updatedAt: String)
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: CaptureSegmentEntity)

    @Query("SELECT * FROM capture_segments WHERE sessionId = :sessionId AND sequence = :sequence")
    suspend fun find(sessionId: String, sequence: Int): CaptureSegmentEntity?

    @Query("UPDATE capture_segments SET assetId = :assetId, state = :state WHERE segmentId = :segmentId")
    suspend fun updateAssetAndState(segmentId: String, assetId: String, state: String)

    @Query("UPDATE capture_segments SET state = 'COMMITTED' WHERE assetId = :assetId")
    suspend fun markCommittedForAsset(assetId: String)

    @Query("SELECT * FROM capture_segments WHERE taskId = :taskId AND assetId = :assetId")
    suspend fun findForAsset(taskId: String, assetId: String): List<CaptureSegmentEntity>
}

@Dao
interface DiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(diagnostic: DiagnosticEntity)

    @Query("SELECT COUNT(*) FROM diagnostics WHERE dedupeKey = :dedupeKey")
    suspend fun countByKey(dedupeKey: String): Int

    @Query("SELECT COUNT(*) FROM diagnostics WHERE code = :code")
    suspend fun countByCode(code: String): Int
}

@Dao
interface RevisionDao {
    @Insert
    suspend fun insert(revision: RevisionEntity)

    @Query("SELECT * FROM revisions WHERE taskId = :taskId ORDER BY revision")
    suspend fun findForTask(taskId: String): List<RevisionEntity>
}
