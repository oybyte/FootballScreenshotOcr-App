# P0A Contract and Module Baseline

## Goal

Freeze the Android-owned v7 JSON contracts, add a Kotlin consumer and v6-to-v7 migration, establish the ten-module Gradle graph, and let the Windows repository validate shared synthetic fixtures without changing its v6 runtime.

## Current State

- Android: master at 3cc03ec; the pre-existing .idea/gradle.xml edit is outside scope.
- Windows at baseline: main at e8a4288; the spreadsheet, staged reports, and untracked output visible then were outside scope. During this task the branch advanced to user commit 4977804 (archive two U23 matches), which committed those reports/output. That commit was not created by this task.
- Android baseline: Gradle 9.4.1, AGP 9.2.1, Kotlin plugin 2.2.10, compile/target SDK 36, min SDK 24. clean test assembleDebug passed before changes.
- Windows baseline: Python 3.11, pytest 651 passed, 1 skipped.
- Android SDK platforms include 36 and 36.1; NDK 26.1.10909125; Build Tools include 36.1.0.
- Windows v6 source of truth is F:\ocr_python_data\src\football_screenshot_ocr\domain.py, with storage migration in storage.py and output semantics in renderer.py, exporter.py, and prompt_bundle.py.
- The Gradle wrapper reports a daemon compatible with Java 21. Its generated daemon property file was refreshed during the baseline build; the configured toolchain remains version 21.

## Invariants

- Android owns the only v7 schemas and golden fixtures.
- Preserve all v6 fields, including outcome/archive/replacement and prematch history.
- Unknown fields and enum values are rejected. Null and missing retain their v6 defaults.
- v6 data migrates with capture_source=legacy and coverage=NOT_RECORDED.
- Screenshot evidence remains asset_id plus normalized original-image bbox; conflicts retain all values and sources.
- Windows v7 runtime and Renderer support remain P0B.
- Existing user changes in both repositories are not staged, overwritten, or cleaned. A user commit advanced the Windows branch during implementation; its archive data files were left intact.

## Scope

- Gradle settings/version catalog and the ten v2.1 modules; minSdk 30, compile/target 36.
- Kotlin Serialization contract models, validation, migration, and focused tests in core.
- JSON Schemas, migration semantics, and synthetic golden/invalid fixtures in the Android repository.
- Windows dev-only JSON Schema dependency, CLI validator, and tests that take the Android contracts path explicitly.
- This ExecPlan and P0A verification evidence.

## Non-Scope

- Windows runtime model/storage/Renderer changes.
- Hilt, Room, OCR, Parser algorithms, LongCapture execution, and full navigation.
- Android IDE preference changes and all pre-existing Windows staged/unstaged/untracked content.
- v7-to-v6 conversion or a claim of cross-platform runtime interoperability.

## Design

core is pure Kotlin/JVM and owns the serializable v7 contract plus v6 migration. parser is pure Kotlin/JVM and depends only on core. Android libraries depend along the v2.1 allowlist, and app assembles all modules. A root Gradle verification task checks the allowlist and graph acyclicity.

Each v6 slot is extended with source, coverage, session/segment references, and diagnostics. CaptureSession v1 stores mode/provider/status, viewport, segments, coverage evidence, replay events, circuit-breaker limits, diagnostics, and resource/model versions. The Python validator loads schemas from the caller-supplied contracts directory and validates the golden fixtures adjacent to that directory.

## Implementation Steps

1. [x] Record Git/toolchain state and run both baseline suites without changing pre-existing work.
2. [x] Add ten modules, version-catalog entries, v7 schemas, migration semantics, Kotlin models/migration, and synthetic fixtures.
3. [x] Add the Windows dev-only validator and shared-fixture schema tests.
4. [x] Run Kotlin, schema, Windows regression, module-boundary, and final clean-build checks.
5. [x] Review both Git diffs and confirm pre-existing changes remain intact.

## Compatibility

The Windows application continues to read and write v6 only. The shared v7 schema rejects unknown fields/enums. Golden fixtures document common semantics but do not certify Windows v7 support. No lossy v7-to-v6 conversion is provided.

## Verification

- Baseline: Android clean test assembleDebug passed; Windows pytest passed with 651 tests and one skip.
- Final Android: clean test assembleDebug verifyModuleBoundaries passed; focused :core/:parser tests passed.
- Final Python contract tests passed with the Android contracts path explicitly supplied. Native Qt initially hit Windows OpenClipboard COM error 0x800401d0; rerunning the UI tests on Qt offscreen passed, followed by the full Windows suite at 658 passed and 1 skipped.
- Inspect schemas with Draft 2020-12 meta-schema validation, validate positive fixtures, and assert invalid fixtures fail for unknown fields, unknown enums, and out-of-range bbox coordinates.
- Recheck Git status and diff in both repositories.

## Risks

- Kotlin serialization defaults must match Pydantic v6 omission/null behavior; tests cover defaults, nulls, and strict decoding.
- A schema fixture can pass syntactic validation while diverging from runtime behavior; Windows runtime compatibility remains pending for P0B.
- The daemon config file is generated and Gradle refreshed its JDK 21 download URLs during baseline; toolchain version remains 21.

## Exit Criteria

- Every declared module compiles and the allowlisted dependency graph is acyclic.
- Kotlin migration preserves every legacy field and assigns legacy/not-recorded metadata.
- Python and Kotlin accept the same positive v7/session fixtures; both reject defined invalid contract cases.
- Android clean tests/debug assembly, Python contract validation, and the full Windows regression suite pass. The full Windows run uses Qt offscreen because the native clipboard was temporarily unavailable in this host session.
- Pre-existing working-tree changes remain present and outside this task's diff.
