# TaskManifest v6 to v7

## Ownership and compatibility

The task-manifest and capture-session schemas in this Android repository are the shared contract source of truth. The Windows application remains a v6 consumer during P0A. Its v7 read/write and Renderer work belongs to P0B.

This migration is one-way. A v7 task must not be serialized as v6 by dropping fields: coverage, capture source, session/segment references, diagnostics, and resource/model versions have no lossless v6 representation.

## v6 fields retained

The v7 root preserves every v6 TaskManifest field and JSON name:

- schema_version, task_id, created_at, updated_at, last_operation_at, status, revision
- fixture, slots, messages, output_path
- outcome, outcome_history, prematch_snapshot, archive_state, replacement_history

Nested v6 objects retain every field defined by the Windows v6 Pydantic model: fixture identity, image assets, field evidence, slot results, overview/timeline/Betfair rows, conflicts, outcome revisions, snapshots, archive state, and replacement history. No known field is silently discarded.

## Defaults, missing values, and null

The migration follows Pydantic v6 defaults:

- Missing scalar fields with defaults receive the documented v6 default, for example status=draft, revision=0, and outcome.status=not_recorded.
- Missing arrays and objects with factories become empty arrays or objects.
- Missing nullable fields become null.
- An explicit null on a nullable field remains null; it is not replaced with a non-null value.
- Required fields without a v6 default remain required, including slot identifiers, evidence text/asset references, result slot identifiers, outcome revision data, and snapshot payload/hash/timestamps.
- Unknown object properties and unknown enum values are rejected. They are never copied through as opaque data.

JSON Schema default is descriptive and does not itself mutate an instance. The Kotlin deserializer and migrateV6ToV7 apply these defaults.

## v7 fields added to TaskManifest and slots

The task root adds capture_session_ids, resource_versions, and model_versions.

Each v7 slot preserves its v6 fields and adds:

- capture_source: legacy, accessibility, media_projection, or manual_import
- coverage: one of the v2.1 coverage states
- capture_session_ids and capture_segment_ids
- structured diagnostics

For every migrated v6 slot, set capture_source=legacy, coverage=NOT_RECORDED, and both reference/diagnostic arrays to empty. At task level, session references are empty and version objects contain null values.

CaptureSession is a separate v1 document. It carries task/slot identity, AUTO/FOLLOW/MANUAL mode, provider, lifecycle status, viewport/insets/orientation, segment asset references, observed movement/stability/anchors/overlap, coverage evidence, replay events, circuit-breaker thresholds, diagnostics, and resource/model versions. COMPLETE requires confirmed top and bottom, connected adjacent segments, and zero unexplained gaps; P0A defines the data contract but does not implement the analyzer.

## Evidence and output invariants

- Screenshot-derived values keep stable asset_id references and LTRB bbox coordinates normalized to the original image in the inclusive numeric range [0, 1].
- Non-screenshot values keep their own source reference and do not require a bbox.
- Conflict values, source asset IDs, and per-asset bboxes are retained together.
- Slot order is the v6 SLOT_ORDER: asian_handicap, asian_handicap_macau, european_odds, european_odds_macau, total_goals, total_goals_macau, kelly, betfair.
- Markdown section and row order remains the existing Windows Renderer order. Macau rows retain source order (newest to oldest where supplied); migration does not sort them.
- Prompt composition removes only the complete heading section named 比赛结果. It does not infer a boundary when that fixed heading is absent.

The synthetic fixtures in test-fixtures/golden cover these invariants without containing real match data or screenshots.
