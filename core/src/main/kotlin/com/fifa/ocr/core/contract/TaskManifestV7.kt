package com.fifa.ocr.core.contract

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private fun nowIso(): String = Instant.now().toString()
private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

@Serializable
data class SourceReference(
    val kind: SourceReferenceKind,
    val id: String,
    val version: String? = null,
)

@Serializable
data class FieldEvidence(
    @SerialName("raw_text") val rawText: String,
    val value: String? = null,
    @SerialName("asset_id") val assetId: String,
    val bbox: NormalizedBbox? = null,
    val confidence: Double? = null,
)

@Serializable
data class ImageAsset(
    @SerialName("asset_id") val assetId: String = newId(),
    val slot: SlotKind,
    val sha256: String,
    @SerialName("relative_path") val relativePath: String,
    val sequence: Int,
    val revision: Int = 1,
    @SerialName("created_at") val createdAt: String = nowIso(),
)

@Serializable
data class FixtureIdentity(
    val competition: String? = null,
    @SerialName("home_team") val homeTeam: String? = null,
    @SerialName("away_team") val awayTeam: String? = null,
    @SerialName("kickoff_display") val kickoffDisplay: String? = null,
    @SerialName("sport_lottery_handicap") val sportLotteryHandicap: Int? = null,
    @SerialName("sport_lottery_serial") val sportLotterySerial: String? = null,
    @SerialName("sport_lottery_source") val sportLotterySource: String? = null,
    @SerialName("user_edited") val userEdited: Boolean = false,
    @SerialName("competition_user_edited") val competitionUserEdited: Boolean = false,
    @SerialName("competition_fixture_id") val competitionFixtureId: String? = null,
    @SerialName("competition_catalog_version") val competitionCatalogVersion: Int? = null,
    @SerialName("competition_catalog_source") val competitionCatalogSource: String? = null,
    @SerialName("field_sources") val fieldSources: Map<String, SourceReference> = emptyMap(),
)

@Serializable
data class MatchScore(
    @SerialName("home_goals") val homeGoals: Int,
    @SerialName("away_goals") val awayGoals: Int,
) {
    init {
        require(homeGoals >= 0 && awayGoals >= 0)
    }
}

@Serializable
data class MatchOutcome(
    val status: OutcomeStatus = OutcomeStatus.NOT_RECORDED,
    @SerialName("score_scope") val scoreScope: String = "regulation_90",
    @SerialName("half_time") val halfTime: MatchScore? = null,
    @SerialName("full_time") val fullTime: MatchScore? = null,
    @SerialName("total_goals") val totalGoals: Int? = null,
    @SerialName("source_kind") val sourceKind: String = "manual",
    val note: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val revision: Int = 0,
) {
    init {
        require(totalGoals == null || totalGoals >= 0)
    }
}

@Serializable
data class OutcomeRevision(
    val outcome: MatchOutcome,
    @SerialName("replaced_at") val replacedAt: String,
    val reason: String? = null,
)

@Serializable
data class PrematchSnapshot(
    val payload: Map<String, kotlinx.serialization.json.JsonElement>,
    @SerialName("asset_sha256") val assetSha256: List<String> = emptyList(),
    @SerialName("snapshot_at") val snapshotAt: String,
    @SerialName("task_revision") val taskRevision: Int,
    @SerialName("snapshot_timing") val snapshotTiming: String = "frozen_before_outcome",
    @SerialName("content_sha256") val contentSha256: String,
)

@Serializable
data class ArchiveState(
    @SerialName("latest_revision") val latestRevision: Int = 0,
    @SerialName("archived_outcome_revision") val archivedOutcomeRevision: Int = 0,
    @SerialName("has_unarchived_changes") val hasUnarchivedChanges: Boolean = false,
)

@Serializable
data class OverviewRow(
    @SerialName("provider_name") val providerName: String,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("provider_raw_name") val providerRawName: String? = null,
    @SerialName("provider_raw_names") val providerRawNames: List<String> = emptyList(),
    @SerialName("provider_confirmed") val providerConfirmed: Boolean = false,
    @SerialName("provider_name_bboxes") val providerNameBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("provider_name_evidences") val providerNameEvidences: List<FieldEvidence> = emptyList(),
    val opening: List<String> = emptyList(),
    val late: List<String> = emptyList(),
    @SerialName("opening_bboxes") val openingBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("late_bboxes") val lateBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("source_assets") val sourceAssets: List<String> = emptyList(),
)

@Serializable
data class MacauTimelineRow(
    @SerialName("displayed_at") val displayedAt: String,
    val status: String = "即",
    val values: List<String> = emptyList(),
    @SerialName("values_bboxes") val valuesBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("return_rate") val returnRate: String? = null,
    @SerialName("kelly_values") val kellyValues: List<String> = emptyList(),
    @SerialName("kelly_bboxes") val kellyBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("source_assets") val sourceAssets: List<String> = emptyList(),
)

@Serializable
data class ConflictRecord(
    val key: String,
    val values: List<String>,
    @SerialName("asset_bboxes") val assetBboxes: List<AssetBbox> = emptyList(),
    @SerialName("source_assets") val sourceAssets: List<String>,
)

@Serializable
data class BetfairRow(
    val selection: String,
    val odds: String? = null,
    @SerialName("traded_volume") val tradedVolume: String? = null,
    @SerialName("profit_loss") val profitLoss: String? = null,
    @SerialName("hot_cold_index") val hotColdIndex: String? = null,
    @SerialName("cell_bboxes") val cellBboxes: List<NormalizedBbox> = emptyList(),
    @SerialName("source_assets") val sourceAssets: List<String> = emptyList(),
)

@Serializable
data class SlotResult(
    val slot: SlotKind,
    val fixture: FixtureIdentity? = null,
    @SerialName("overview_rows") val overviewRows: List<OverviewRow> = emptyList(),
    @SerialName("timeline_rows") val timelineRows: List<MacauTimelineRow> = emptyList(),
    @SerialName("betfair_rows") val betfairRows: List<BetfairRow> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val evidences: List<FieldEvidence> = emptyList(),
    val conflicts: List<ConflictRecord> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
data class CaptureDiagnostic(
    val code: DiagnosticCode,
    val severity: DiagnosticSeverity,
    val message: String,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("segment_id") val segmentId: String? = null,
    @SerialName("asset_id") val assetId: String? = null,
)

@Serializable
data class SlotRecordV7(
    val slot: SlotKind,
    val state: SlotState = SlotState.EMPTY,
    val assets: List<ImageAsset> = emptyList(),
    val result: SlotResult? = null,
    val messages: List<String> = emptyList(),
    val revision: Int = 0,
    @SerialName("capture_source") val captureSource: CaptureSource = CaptureSource.LEGACY,
    val coverage: CoverageState = CoverageState.NOT_RECORDED,
    @SerialName("capture_session_ids") val captureSessionIds: List<String> = emptyList(),
    @SerialName("capture_segment_ids") val captureSegmentIds: List<String> = emptyList(),
    val diagnostics: List<CaptureDiagnostic> = emptyList(),
)

@Serializable
data class ReplacementHistory(
    @SerialName("replaced_at") val replacedAt: String,
    @SerialName("previous_hashes") val previousHashes: Map<String, String> = emptyMap(),
)

@Serializable
data class ResourceVersions(
    @SerialName("provider_catalog") val providerCatalog: String? = null,
    @SerialName("layout_catalog") val layoutCatalog: String? = null,
    val prompt: String? = null,
    val template: String? = null,
)

@Serializable
data class ModelVersions(
    @SerialName("ocr_model") val ocrModel: String? = null,
    @SerialName("ocr_model_sha256") val ocrModelSha256: String? = null,
    val preprocessing: String? = null,
)

@Serializable
data class TaskManifestV7(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("task_id") val taskId: String = newId(),
    @SerialName("created_at") val createdAt: String = nowIso(),
    @SerialName("updated_at") val updatedAt: String = nowIso(),
    @SerialName("last_operation_at") val lastOperationAt: String? = null,
    val status: TaskStatus = TaskStatus.DRAFT,
    val revision: Int = 0,
    val fixture: FixtureIdentity? = null,
    val slots: Map<SlotKind, SlotRecordV7> = emptyMap(),
    val messages: List<String> = emptyList(),
    @SerialName("output_path") val outputPath: String? = null,
    val outcome: MatchOutcome = MatchOutcome(),
    @SerialName("outcome_history") val outcomeHistory: List<OutcomeRevision> = emptyList(),
    @SerialName("prematch_snapshot") val prematchSnapshot: PrematchSnapshot? = null,
    @SerialName("archive_state") val archiveState: ArchiveState = ArchiveState(),
    @SerialName("replacement_history") val replacementHistory: ReplacementHistory? = null,
    @SerialName("capture_session_ids") val captureSessionIds: List<String> = emptyList(),
    @SerialName("resource_versions") val resourceVersions: ResourceVersions = ResourceVersions(),
    @SerialName("model_versions") val modelVersions: ModelVersions = ModelVersions(),
) {
    init {
        require(schemaVersion == 7) { "TaskManifestV7 requires schema_version 7" }
        require(slots.all { (key, record) -> key == record.slot }) { "Slot map key must match slot record slot" }
    }
}
