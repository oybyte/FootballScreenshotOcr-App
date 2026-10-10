# 权限状态与进程恢复可靠性修复

## Goal

首屏权限状态在查询完成前显示“正在查询”，查询完成后准确区分系统授权、进程连接和本次投影会话；低存储和进程强杀不产生伪 `COMMITTED`、`IMAGES_READY` 或任务 revision，并可在启动 reconciliation 完成后继续恢复。

## Current State

- 分支为 `main`，P2 恢复修复已提交，Room schema 为 1。
- `MainActivity` 使用全 `false` 快照初始化，`onResume()` 后才刷新；`StorageRuntime.initialize()` 后台启动 reconciliation 但没有 readiness 状态。
- `CaptureFileStore` 写 pending 文件前没有空间预检；`RecoveryProbeService` 可在故障点杀进程，但没有记录探针上下文。
- `emulator-5554` 已有 instrumentation 通过证据；P1 权限手工矩阵、低存储、真实进程强杀和 OEM 验证仍为 `pending`。

## Invariants

- 遵守根 `AGENTS.md`、`:app/AGENTS.md`、`:data/AGENTS.md` 和 Decision 0005；不改变 v7 Schema、Windows、OCR、Parser、LongCapture 或 Internal APK 路线。
- Accessibility/Overlay 系统授权、Accessibility/浮窗服务连接和 MediaProjection 一次性会话必须分开表达；投影 token 不持久化。
- 原图遵循 pending、写入、校验、原子改名、Room 提交顺序；低存储和写入异常必须保留可恢复文件与诊断。
- reconciliation 必须幂等；低存储不得更新 slot、task revision 或插入 `ASSET_COMMITTED` Revision。

## Scope

- `:app` 权限 UiState、Compose 状态展示/按钮门禁和相关测试。
- `:data` 存储健康查询、低存储错误处理、`StorageRuntime` readiness 和 instrumentation 测试。
- debug-only `RecoveryProbeService` 的进程上下文记录和恢复验证接线。
- P1/P2 ExecPlan 的事实、测试和 pending 证据更新。

## Non-Scope

- 不修改 v7 合同、Room schema 版本、Windows/Python、OCR、Parser、LongCapture 算法、完整任务 UI 或 Play 发布政策。
- 不修改 `.idea` 用户文件，不删除已有 pending 文件，不改写测试 expected 以制造通过。
- 真机 OEM 兼容和 Play 审核仍只记录为后续验证。

## Design

- `CaptureWorkbenchUiState.permissions` 和 `overlayRunning` 使用 `null` 表示尚未完成查询；Screen 显示“正在查询”，采集按钮关闭。刷新成功后保留旧快照直到新快照可用。
- `CaptureFileStore` 提供 `StorageHealth` 查询。Android 实现用 `StatFs` 读取物理可用字节，并用公开 `StorageManager.getAllocatableBytes()` 得到系统认可的安全分配量，二者之差作为安全余量；Android SDK 未公开直接读取 low-storage threshold 的 API。`RoomCaptureStore` 在 pending 记录后按 `Bitmap.allocationByteCount + systemReserveBytes` 预检，并在写入异常中识别 `ENOSPC`。失败标记 `RECOVERY_REQUIRED`、写 `STORAGE_LOW`，保留文件和 Room 关系。
- `StorageRuntime` 发布 `UNINITIALIZED/RECONCILING/READY/FAILED`，先执行一次启动 session 中断，再运行 reconciliation；通用 reconciliation 本身保持可重复幂等。只有两步完成后才为 `READY`；`awaitReady()` 在失败时抛出并保留失败原因。
- 进程探针记录 task/session/failure point；重启后由 runtime readiness 作为恢复检查的前置条件，第二次 reconciliation 验证幂等。

## Implementation Steps

- [x] 新建本计划并核对模块规则、现有测试和设备状态。
- [x] 将权限和悬浮运行状态改为 Loading/Ready 语义，加入 projection/floating 状态 reducer 和 Compose 测试。
- [x] 增加 `StorageHealth`、低存储预检/`ENOSPC` 处理及 data instrumentation 覆盖。
- [x] 增加 `StorageRuntime` readiness/await API，扩展 debug 探针上下文并补恢复测试接线。
- [x] 运行单元、assemble、模块边界、data/app instrumentation 和 diff 检查。
- [blocked] 用 `emulator-5554` 执行可行的 P1 手工矩阵；2026-10-10 当前 Codex UI 自动化没有可绑定的模拟器窗口，不能安全执行设置页和系统授权点击，未用 ADB 输入替代；未完成场景保留 `pending`。
- [x] 更新 P1/P2 计划和本计划的 Verification/Exit Criteria，复核工作区只含授权范围变更。

## Compatibility

Room schema 保持 1，不新增持久化字段或 migration；v7 类型和文件布局不变。Internal debug APK 保留 debug 探针，release 变体不编译该源码集。已有任务的权限 UI 只改变未知状态呈现，已确认状态语义不变。

## Verification

已执行 `gradlew.bat test assembleDebug verifyModuleBoundaries --no-configuration-cache`（通过）、`:data:connectedDebugAndroidTest --no-configuration-cache`（emulator-5554，12/12，通过）、`:app:connectedDebugAndroidTest --no-configuration-cache`（emulator-5554，10/10，通过）、`:app:processReleaseManifest --no-configuration-cache`（通过）和 `git diff --check`（通过）。`gradlew.bat clean test assembleDebug verifyModuleBoundaries --no-configuration-cache` 曾因 Windows 进程占用 `data/build/.../classes.jar` 无法删除而在 `:data:clean` 失败；同一门禁已在不含 clean 的完整构建中通过。低存储、reconciliation 幂等和 Compose Loading 有直接测试证据；真实强杀、手工权限矩阵和 OEM 结果按设备证据单独记录。

2026-10-10 验证记录：`.agents/scripts/verify-process-recovery.ps1 -Serial emulator-5554` 已逐个强杀并验证 `AFTER_PENDING_INSERT`、`AFTER_TEMP_WRITE`、`AFTER_VALIDATION`、`AFTER_RENAME_BEFORE_DB_COMMIT`、`AFTER_DB_COMMIT`；每个点重启后等待 `StorageRuntime.READY`，探针校验通过，第二次 reconciliation 保持幂等。release 合并 manifest 不含 `RecoveryProbeService`。模拟器为 API 34、720x1280；设备身份字段存在冲突，只能作为 emulator 证据。当天尝试继续 P1 系统权限手工矩阵时，UI 自动化没有发现可绑定的模拟器窗口，未点击授权弹窗或使用 ADB 注入输入，因此 Accessibility、Overlay、MediaProjection、Photo Picker/Sharesheet、FLAG_SECURE 实机路径、悬浮入口恢复和三次连续采集仍为 `pending`。

## Risks

空间预检与实际写入之间存在竞态，且系统安全分配量是公开 API 对低存储阈值的近似，必须保留写入异常和 pending 文件；runtime readiness 失败不能被误报为 READY；广播可能先于首个完整权限快照到达，必须忽略部分状态并等待刷新；模拟器结果不能外推 OEM 或 Play 合规。

## Exit Criteria

- 首次 Compose 渲染不显示“未授权”，未知状态采集按钮不可执行；已完成查询的 false 快照仍正确显示未授权。
- 低存储和 `ENOSPC` 测试证明没有 `COMMITTED`、`IMAGES_READY`、task revision 或 `ASSET_COMMITTED` Revision，且诊断去重、文件可恢复。
- `StorageRuntime` 在 reconciliation 完成前不为 READY；失败可观察且可等待。
- 各故障点重启后文件与 Room 状态可解释，`AFTER_DB_COMMIT` 和第二次 reconciliation 不重复 revision。
- 已执行的构建/测试命令和未能执行的手工/OEM 项目均在计划中如实标注。
