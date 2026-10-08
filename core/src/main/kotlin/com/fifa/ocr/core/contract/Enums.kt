package com.fifa.ocr.core.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SlotKind {
    @SerialName("asian_handicap") ASIAN_HANDICAP,
    @SerialName("asian_handicap_macau") ASIAN_HANDICAP_MACAU,
    @SerialName("european_odds") EUROPEAN_ODDS,
    @SerialName("european_odds_macau") EUROPEAN_ODDS_MACAU,
    @SerialName("total_goals") TOTAL_GOALS,
    @SerialName("total_goals_macau") TOTAL_GOALS_MACAU,
    @SerialName("kelly") KELLY,
    @SerialName("betfair") BETFAIR,
}

val SLOT_ORDER = listOf(
    SlotKind.ASIAN_HANDICAP,
    SlotKind.ASIAN_HANDICAP_MACAU,
    SlotKind.EUROPEAN_ODDS,
    SlotKind.EUROPEAN_ODDS_MACAU,
    SlotKind.TOTAL_GOALS,
    SlotKind.TOTAL_GOALS_MACAU,
    SlotKind.KELLY,
    SlotKind.BETFAIR,
)

@Serializable
enum class TaskStatus {
    @SerialName("draft") DRAFT,
    @SerialName("queued") QUEUED,
    @SerialName("recognizing") RECOGNIZING,
    @SerialName("ready") READY,
    @SerialName("blocked") BLOCKED,
    @SerialName("finished") FINISHED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class SlotState {
    @SerialName("empty") EMPTY,
    @SerialName("images_ready") IMAGES_READY,
    @SerialName("user_marked_flat") USER_MARKED_FLAT,
    @SerialName("recognizing") RECOGNIZING,
    @SerialName("valid") VALID,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
}

@Serializable
enum class OutcomeStatus {
    @SerialName("not_recorded") NOT_RECORDED,
    @SerialName("finished") FINISHED,
    @SerialName("void") VOID,
    @SerialName("abandoned") ABANDONED,
}

@Serializable
enum class CaptureSource {
    @SerialName("legacy") LEGACY,
    @SerialName("accessibility") ACCESSIBILITY,
    @SerialName("media_projection") MEDIA_PROJECTION,
    @SerialName("manual_import") MANUAL_IMPORT,
}

@Serializable
enum class CoverageState {
    NOT_APPLICABLE,
    NOT_RECORDED,
    COMPLETE,
    LIKELY_COMPLETE,
    GAP_SUSPECTED,
    INCOMPLETE,
}

@Serializable
enum class DiagnosticCode {
    TOP_UNKNOWN,
    BOTTOM_UNKNOWN,
    SCROLL_BLOCKED,
    STABILITY_TIMEOUT,
    GAP_SUSPECTED,
    CAPTURE_BLOCKED,
}

@Serializable
enum class DiagnosticSeverity {
    @SerialName("info") INFO,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
}

@Serializable
enum class SourceReferenceKind {
    @SerialName("manual") MANUAL,
    @SerialName("catalog") CATALOG,
    @SerialName("network") NETWORK,
    @SerialName("import") IMPORT,
}
