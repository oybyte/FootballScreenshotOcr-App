# P1 Android Single-Device Capture Spike

## Goal

Replace the sample screen with a minimal capture workbench that can capture the foreground display from a floating entry point, preview it in memory, and import an image through Photo Picker or Android Sharesheet. Verify the capture permission and fallback paths on the user's single daily-use device when its identity is confirmed.

## Current State

- Android repository is on `main` at local commit `901f619`, one commit ahead of `origin/main`; do not rewrite or push unrelated history.
- P0A modules and v7 `CaptureSessionV1`/diagnostic contracts exist. `:feature-capture` has only a Gradle skeleton; the app is still the Compose greeting sample and its manifest has no capture services or permissions.
- `.idea/compiler.xml`, `.idea/gradle.xml`, and `.idea/misc.xml` have pre-existing user modifications; preserve them.
- ADB exposes `emulator-5554`, API 34 / Android 14, but reports model `25060RK16C`, manufacturer `REDMI`, and a Samsung build fingerprint. Device identity is unconfirmed, so report device acceptance as pending until reconciled.
- Android API guidance confirms API 34+ MediaProjection foreground-service declaration, per-session consent and one VirtualDisplay creation per projection token. Accessibility screenshot APIs are available from minSdk 30. Android Sharesheet image intake uses content URIs.

## Invariants

- Follow root `AGENTS.md`, `app/AGENTS.md`, decisions 0002/0005, and `.agents/skills/long-capture/SKILL.md`.
- Product is local-first: no upload, prediction, persistence, OCR, or task mutation in P1. Preview Bitmap lifetime is in memory only.
- Business/UI orchestration depends on `CaptureProvider`, not directly on AccessibilityService or MediaProjection APIs.
- Starting or revoking permissions must be observable. Failed, blocked, revoked, or blank capture must never be represented as success.
- A secure-window error maps to existing v7 `CAPTURE_BLOCKED`; the stable diagnostic message is `CAPTURE_BLOCKED_BY_WINDOW_SECURITY`.
- Hide the overlay before capture and restore it in a `finally` path. Projection consent is per session and its token creates at most one VirtualDisplay.
- The accepted release target is Internal APK; do not claim Google Play policy approval.

## Scope

- `:feature-capture`: provider contract, capture failures, Accessibility screenshot service/provider, one-session MediaProjection foreground service/provider, floating overlay service, bounded image URI decoder, and focused unit tests.
- `:app`: explicit P1 composition, permission/status workbench, Photo Picker and Sharesheet intake, in-memory preview, secure-window test fixture, and Compose tests.
- App/library manifests, service configuration resources, version catalog only for dependencies required by this implementation.
- Update this plan with evidence and unresolved device-only verification.

## Non-Scope

- Windows/Python repository and P0B.
- Room, file persistence, task JSON/Markdown output, OCR, scrolling, LongCapture, Hilt, Navigation, and product workflow UI.
- Play Store policy review or multi-device/API matrix.
- Changes to P0A schemas, v6/v7 compatibility, `.idea` files, or existing user history.

## Design

- `MainActivity` owns Activity Result launchers, permission refresh on resume, the P1 Compose screen, and explicit provider composition. It keeps the last `CapturedFrame` in memory only.
- A local broadcast from the floating service reaches the live activity in the same app process. The service hides its window before dispatch; the activity waits briefly, tries Accessibility first, falls back to an already-armed MediaProjection session, and always restores the overlay.
- If Accessibility fails without an armed projection, show the exact failure and the foreground-only action to obtain projection consent; do not launch an Activity from the background overlay service.
- The projection service starts foreground before obtaining `MediaProjection`, owns one ImageReader/VirtualDisplay session, serves one frame request, then releases the projection session. The user may grant a new session for another attempt.
- Image import accepts one image URI from Photo Picker, `ACTION_SEND`, or compatible `ClipData`; validate MIME/readability and source dimensions, then decode with bounded sampling. No URI is persisted.
- Secure-window error is mapped explicitly. Unknown/all-black output is surfaced as content unavailable, never promoted to a secure-window diagnosis without an API error.

## Implementation Steps

1. [x] Add provider contracts and pure capture policy/error/URI parsing helpers; add tests for permission fallback, secure-window mapping, URI selection, and image dimension limits.
2. [x] Implement the AccessibilityService and screenshot provider; convert HardwareBuffer to a software Bitmap, release platform buffers, detect unusable blank frames, and expose service connectivity.
3. [x] Implement the MediaProjection foreground service and provider with per-session consent, one VirtualDisplay, one-shot frame delivery, stop/revocation cleanup, and notification.
4. [x] Implement floating foreground service, permission/settings actions, hide/capture/restore orchestration, and foreground-safe projection fallback.
5. [x] Replace greeting with the P1 workbench, permission status, picker/share intake, errors/loading states, and memory-only preview; add Compose state/interaction tests and a FLAG_SECURE instrumentation fixture.
6. [in progress] Run unit/instrumented tests and `gradlew.bat clean test assembleDebug`; install and exercise the build only after confirming the connected target is the intended phone. Update this plan and Decision 0005 fact status from observed evidence.

## Compatibility

- `minSdk=30`, `compileSdk=36`, `targetSdk=36` stay unchanged. Accessibility screenshot is guarded by API availability and service configuration.
- MediaProjection supports Android 14+ mandatory typed foreground service; older supported versions use the same provider with platform-appropriate service startup.
- Existing v7 enums and schemas remain unchanged. Capture source can be represented by existing `CaptureProvider`; secure capture uses existing `CAPTURE_BLOCKED`.
- Photo Picker and Sharesheet provide temporary content URI grants; decoder reads only during import and retains only the decoded Bitmap in memory.
- No persisted v6/v7 task or Windows behavior changes.

## Verification

- Unit tests: provider error mapping, secure-window classification, provider fallback policy, shared image URI selection, image size rejection and bounded decode calculations.
- Compose UI tests: permission states, enabled/disabled actions, loading/failure/success preview, image import success/failure.
- Instrumented test: a `FLAG_SECURE` test activity is actually secure and its screenshot error maps to `CAPTURE_BLOCKED`; this does not replace manual validation of OEM Accessibility behavior.
- Build: `gradlew.bat clean test assembleDebug verifyModuleBoundaries`.
- Device: confirm serial/model/manufacturer/API/fingerprint; exercise accessibility grant/revoke, overlay grant/revoke, clean overlay capture, projection consent/denial/revocation, Photos Picker, Sharesheet, and secure-window failure. If identity remains ambiguous or only an emulator is connected, leave device acceptance pending.
- Review `git diff --check` and ensure only Android P1 files plus this plan changed; preserve IDE changes.

### Evidence (2026-10-08)

- `gradlew.bat clean test assembleDebug verifyModuleBoundaries`: passed in this validation run. All declared modules completed their build tasks, code-bearing modules compiled, the Debug APK assembled, and the module-boundary task passed. Gradle reports one configuration-cache warning because `verifyModuleBoundaries` inspects Gradle script objects; the task itself passed.
- Unit-test reports: 16 tests passed across `:app`, `:core`, `:feature-capture`, and `:parser`; 0 failures, 0 errors, 0 skipped.
- `gradlew.bat connectedDebugAndroidTest`: passed on `emulator-5554` (API 34); 4 instrumentation tests passed, including the secure-window fixture and capture workbench UI tests.
- `git diff --check`: passed.
- Debug APK installed and `com.fifa.ocr/.MainActivity` launched on ADB target `emulator-5554`; no crash observed in launch log and the P1 workbench rendered.
- Device-only acceptance remains `pending`: connected target reports API 34, model `25060RK16C`, manufacturer `REDMI`, device `star2qltechn`, and Samsung build fingerprint `samsung/star2qltezh/star2qltechn:14/...`; this is an emulator-like target with conflicting identity, not confirmed as the user's daily phone. Accessibility/overlay interaction was exercised earlier on this target, but projection consent/revocation, picker/share intake, and confirmed real-device behavior remain pending.

## Risks

- Some OEMs disable or alter Accessibility screenshot support; explicit failure and manual/import paths remain available.
- MediaProjection image delivery is asynchronous and can be stopped by the user or device lock; listener, timeout, callback, display, and ImageReader resources must be released on every exit.
- `FLAG_SECURE` may produce a secure error or a blank frame depending on platform/provider. Only the explicit platform error receives the secure-window code; ambiguous blank output remains content unavailable.
- Android 14+ selected-app projection can capture only the chosen app. The workbench must explain how to choose the target app/entire display and must never preview the workbench as if it were the target when the captured region does not match expectations.
- Connected ADB identity currently conflicts across properties. Device acceptance and marking Decision 0005 live-verified are blocked until identity is confirmed.

## Exit Criteria

- All declared modules compile; unit and Compose tests pass; secure-window mapping test passes; no success state is emitted on permission/provider/decode failure.
- Floating capture excludes the overlay and restores it after success and failure; image picker and Sharesheet can produce an in-memory preview.
- MediaProjection uses fresh consent for each session, creates one virtual display per token, and releases resources when stopped or after the one-shot capture.
- Clean build passes. One-phone manual verification is marked live-verified only if the connected device identity is confirmed and each listed path is observed; otherwise the plan and final report explicitly retain the device items as pending.
