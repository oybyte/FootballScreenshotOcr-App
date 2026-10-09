package com.fifa.ocr.data.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val revision: Int,
    val outputState: String,
)

@Entity(
    tableName = "fixtures",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["taskId"], childColumns = ["taskId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("taskId")],
)
data class FixtureEntity(
    @PrimaryKey val taskId: String,
    val competition: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val kickoffDisplay: String? = null,
    val sourceJson: String = "{}",
    val userEdited: Boolean = false,
)

@Entity(
    tableName = "slots",
    primaryKeys = ["taskId", "slotKind"],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["taskId"], childColumns = ["taskId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("taskId")],
)
data class SlotEntity(
    val taskId: String,
    val slotKind: String,
    val state: String,
    val coverage: String,
    val captureSource: String? = null,
    val revision: Int = 0,
    val message: String? = null,
)

@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["taskId"], childColumns = ["taskId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SlotEntity::class, parentColumns = ["taskId", "slotKind"], childColumns = ["taskId", "slotKind"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("taskId", "sha256"), Index("taskId", "slotKind"), Index("taskId", "assetId", unique = true)],
)
data class AssetEntity(
    @PrimaryKey val assetId: String,
    val taskId: String,
    val slotKind: String,
    val sequence: Int,
    val sha256: String? = null,
    val relativePath: String,
    val fileSize: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: String,
    val writeState: String,
)

@Entity(
    tableName = "capture_sessions",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["taskId"], childColumns = ["taskId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("taskId"), Index("taskId", "slotKind"), Index("taskId", "sessionId", unique = true)],
)
data class CaptureSessionEntity(
    @PrimaryKey val sessionId: String,
    val taskId: String,
    val slotKind: String,
    val mode: String,
    val provider: String,
    val status: String,
    val startedAt: String,
    val updatedAt: String,
    val viewportJson: String? = null,
    val coverage: String,
    val coverageEvidenceJson: String = "{}",
    val replayJson: String = "{\"events\":[]}",
    val circuitBreakerJson: String = "{}",
    val resourceVersionsJson: String = "{}",
    val modelVersionsJson: String = "{}",
    val revision: Int = 0,
    val pausedFromStatus: String? = null,
)

@Entity(
    tableName = "capture_segments",
    foreignKeys = [
        ForeignKey(entity = CaptureSessionEntity::class, parentColumns = ["taskId", "sessionId"], childColumns = ["taskId", "sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AssetEntity::class, parentColumns = ["taskId", "assetId"], childColumns = ["taskId", "assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId", "sequence", unique = true), Index("assetId"), Index("taskId"), Index("taskId", "sessionId"), Index("taskId", "assetId")],
)
data class CaptureSegmentEntity(
    @PrimaryKey val segmentId: String,
    val taskId: String,
    val sessionId: String,
    val assetId: String,
    val sequence: Int,
    val capturedAt: String,
    val state: String,
    val scrollRequest: Double? = null,
    val observedMovement: Double? = null,
    val stabilityResult: String = "unknown",
    val anchorJson: String = "[]",
    val connectedToPrevious: Boolean? = null,
    val overlapRatio: Double? = null,
)

@Entity(
    tableName = "diagnostics",
    indices = [Index("taskId"), Index("sessionId"), Index("segmentId"), Index("assetId"), Index("dedupeKey", unique = true)],
)
data class DiagnosticEntity(
    @PrimaryKey val diagnosticId: String,
    val taskId: String?,
    val sessionId: String?,
    val segmentId: String?,
    val assetId: String?,
    val code: String,
    val severity: String,
    val message: String,
    val createdAt: String,
    val dedupeKey: String,
)

@Entity(
    tableName = "revisions",
    primaryKeys = ["taskId", "revision"],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["taskId"], childColumns = ["taskId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("taskId")],
)
data class RevisionEntity(
    val taskId: String,
    val revision: Int,
    val operation: String,
    val changedAt: String,
    val source: String,
    val contentHash: String? = null,
)
