package com.fifa.ocr.core.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CaptureMode {
    AUTO,
    FOLLOW,
    MANUAL,
}

@Serializable
enum class CaptureProvider {
    @SerialName("accessibility") ACCESSIBILITY,
    @SerialName("media_projection") MEDIA_PROJECTION,
    @SerialName("manual_import") MANUAL_IMPORT,
}

@Serializable
enum class CaptureSessionStatus {
    PREPARING,
    TOP_PENDING,
    TOP_CONFIRMED,
    CAPTURING,
    WAITING_STABLE,
    SEGMENT_COMMITTED,
    WAITING_USER,
    PAUSED,
    BOTTOM_PENDING,
    BOTTOM_CONFIRMED,
    INTERRUPTED,
    REVIEW_REQUIRED,
    COMPLETED,
    FAILED,
}

@Serializable
enum class ViewportOrientation {
    portrait,
    landscape,
    unknown,
}

@Serializable
enum class StabilityResult {
    stable,
    unstable,
    unknown,
}

@Serializable
enum class EndEvidence {
    scroll_boundary,
    page_structure,
    content_stability,
    user_confirmed,
    unknown,
}

@Serializable
enum class ReplayEventType {
    scroll_requested,
    segment_committed,
    stability_observed,
    coverage_decided,
    paused,
    resumed,
}

@Serializable
data class Insets(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
) {
    init {
        require(top >= 0 && right >= 0 && bottom >= 0 && left >= 0)
    }
}

@Serializable
data class Viewport(
    val width: Int,
    val height: Int,
    val insets: Insets,
    val orientation: ViewportOrientation,
) {
    init {
        require(width > 0 && height > 0)
    }
}

@Serializable
data class CaptureSegment(
    @SerialName("segment_id") val segmentId: String,
    @SerialName("asset_id") val assetId: String,
    val sequence: Int,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("scroll_request") val scrollRequest: Double? = null,
    @SerialName("observed_movement") val observedMovement: Double? = null,
    @SerialName("stability_result") val stabilityResult: StabilityResult,
    @SerialName("anchor_ids") val anchorIds: List<String>,
    @SerialName("connected_to_previous") val connectedToPrevious: Boolean? = null,
    @SerialName("overlap_ratio") val overlapRatio: Double? = null,
    @SerialName("ocr_reference") val ocrReference: String? = null,
) {
    init {
        require(segmentId.isNotBlank() && assetId.isNotBlank() && sequence > 0)
        require(overlapRatio == null || overlapRatio in 0.0..1.0)
    }
}

@Serializable
data class CoverageRegion(
    val start: Double,
    val end: Double,
    @SerialName("segment_id") val segmentId: String?,
) {
    init {
        require(start >= 0.0 && end >= start)
    }
}

@Serializable
data class CoverageEvidence(
    @SerialName("top_confirmed") val topConfirmed: Boolean?,
    @SerialName("bottom_confirmed") val bottomConfirmed: Boolean?,
    @SerialName("adjacent_segments_connected") val adjacentSegmentsConnected: Boolean?,
    @SerialName("segment_count") val segmentCount: Int,
    @SerialName("minimum_overlap") val minimumOverlap: Double?,
    @SerialName("gap_count") val gapCount: Int,
    @SerialName("gap_regions") val gapRegions: List<CoverageRegion>,
    @SerialName("duplicate_regions") val duplicateRegions: List<CoverageRegion>,
    @SerialName("scroll_progress") val scrollProgress: Double?,
    @SerialName("end_evidence") val endEvidence: EndEvidence,
) {
    init {
        require(segmentCount >= 0 && gapCount >= 0)
        require(minimumOverlap == null || minimumOverlap in 0.0..1.0)
        require(scrollProgress == null || scrollProgress in 0.0..1.0)
    }
}

@Serializable
data class ReplayEvent(
    val sequence: Int,
    val at: String,
    @SerialName("event_type") val eventType: ReplayEventType,
    @SerialName("requested_scroll") val requestedScroll: Double? = null,
    @SerialName("observed_movement") val observedMovement: Double? = null,
    @SerialName("segment_id") val segmentId: String? = null,
    val coverage: CoverageState? = null,
    val message: String? = null,
) {
    init {
        require(sequence > 0)
    }
}

@Serializable
data class ReplayLog(val events: List<ReplayEvent>)

@Serializable
data class CircuitBreaker(
    @SerialName("max_segments") val maxSegments: Int,
    @SerialName("max_scroll_attempts") val maxScrollAttempts: Int,
    @SerialName("max_no_progress") val maxNoProgress: Int,
    @SerialName("max_stability_wait_ms") val maxStabilityWaitMs: Long,
    @SerialName("max_session_duration_ms") val maxSessionDurationMs: Long,
) {
    init {
        require(maxSegments > 0 && maxScrollAttempts > 0 && maxNoProgress > 0)
        require(maxStabilityWaitMs > 0 && maxSessionDurationMs > 0)
    }
}

@Serializable
data class CaptureSessionV1(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("session_id") val sessionId: String,
    @SerialName("task_id") val taskId: String,
    val slot: SlotKind,
    val mode: CaptureMode,
    val provider: CaptureProvider,
    val status: CaptureSessionStatus,
    @SerialName("started_at") val startedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    val viewport: Viewport? = null,
    val segments: List<CaptureSegment>,
    val coverage: CoverageState,
    @SerialName("coverage_evidence") val coverageEvidence: CoverageEvidence,
    val diagnostics: List<CaptureDiagnostic>,
    val replay: ReplayLog,
    @SerialName("circuit_breaker") val circuitBreaker: CircuitBreaker,
    @SerialName("resource_versions") val resourceVersions: ResourceVersions,
    @SerialName("model_versions") val modelVersions: ModelVersions,
) {
    init {
        require(schemaVersion == 1)
        require(sessionId.isNotBlank() && taskId.isNotBlank())
        require(segments.zipWithNext().all { (a, b) -> a.sequence < b.sequence })
        if (coverage == CoverageState.COMPLETE) {
            require(coverageEvidence.topConfirmed == true)
            require(coverageEvidence.bottomConfirmed == true)
            require(coverageEvidence.adjacentSegmentsConnected == true)
            require(coverageEvidence.gapCount == 0 && coverageEvidence.gapRegions.isEmpty())
        }
    }
}
