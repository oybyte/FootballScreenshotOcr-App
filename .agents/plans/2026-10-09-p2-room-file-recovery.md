# P2：Room、原图文件事务与进程恢复

## Goal

建立 Android 端任务元数据和原图可靠保存能力。Room 保存可查询事实，应用私有文件保存原图；文件写入失败、进程中断或数据库部分提交后，启动 reconciliation 能保留证据并恢复可解释状态。

## Current State

- 分支为 `main`，基线提交为 `a6248b6`。
- 当前 `:data` 仅依赖 `:core`，已接入 Room/KSP、schema 1、数据库、文件仓库和启动 reconciliation。
- `:feature-capture` 已有 Android `CapturedFrame`，但 `:data` 不得依赖 feature；持久化入口使用 data-owned `PersistableFrame` 适配，保持模块无环。
- `:core` 已冻结 v7 会话枚举和契约类型。没有历史 Room 数据库，初始 schema 为 1。

## Invariants

- Room 是运行时任务事实源；原图不进入 Room BLOB；DataStore、Markdown 和 JSON 不作为任务事实兜底。
- 原图写入顺序为 pending 记录、临时文件、force/校验、atomic rename、Room committed；任何无法解释的文件不得静默删除。
- 未确认完整的任务不得进入 READY/FINISHED；非终态会话进程启动后标记 INTERRUPTED 并记录诊断。
- 同一任务按 SHA-256 去重，跨任务不复用 AssetEntity。文件名不得包含赛事或球队隐私。
- 不修改 Windows、v7 Schema、P1 权限逻辑、OCR、Parser、LongCapture 或完整 UI。

## Scope

- `:data` 的 Room entities/DAOs/database、CaptureStore、文件存储和 reconciliation。
- Room/KSP 版本登记、schema export、测试替身和故障注入。
- `:feature-capture` 到 `:data` 的 `CapturedFrame` 持久化适配，仅在已存在的采集边界接入。
- 单元测试、Room 内存数据库测试和 debug/test-only 恢复探针所需的最小接线。

## Non-Scope

- OCR、模型、Parser、Merge、Validator、LongCapture 滚动、Coverage 算法、结果导出、完整任务 UI。
- MediaProjection 授权持久化、Windows/Python 改动、真实 OEM 兼容和发布政策审核。

## Design

`CaptureStore.persistFrame` 在 `:data` 接收 `PersistableFrame`，先写 Asset/Segment pending，再由 `CaptureFileStore` 将 Bitmap 或 URI 字节写入同一文件系统的 pending 临时文件。验证尺寸、解码和流式 SHA-256 后原子重命名，最后在一个 Room transaction 中提交 Asset、Segment、Slot/Session revision 和 Revision。故障点可注入并保留文件。

启动时 reconciliation 扫描 Room pending 状态、pending 临时文件、孤儿 committed 文件、缺失/哈希不一致引用和非终态 Session。修复只执行可解释且幂等的动作；无法判断归属时写诊断并保留文件。

本次修复补齐 pending/committed 文件恢复后的 slot、session、task revision 和 Revision 元数据；没有对应 AssetEntity 的 pending 文件只记录 `ORPHAN_PENDING_FILE` 诊断并保留原文件。

## Implementation Steps

- [x] 复核基线、模块边界、契约和 storage-migration 规则。
- [x] 加入 Room/KSP 依赖、schema export 和 data 层关系模型。
- [x] 实现文件阶段事务、哈希去重、故障注入和 CaptureStore。
- [x] 实现 session 暂停/继续、启动中断和 reconciliation。
- [x] 增加 Room、文件故障、恢复和关系约束测试。
- [x] 修复 reconciliation 恢复元数据，并报告未关联的 pending 文件。
- [x] 运行全量 Gradle/Android 测试、检查 diff，并按实际结果更新本计划。

## Compatibility

初始 Room schema 为 1，不存在旧数据库迁移；后续 schema 变化必须提供显式 Migration。v7 契约类型保持不变。P1 内存预览继续工作，P2 只增加可选持久化边界，不宣称跨端 v7 运行时兼容。

## Verification

已执行 `:data:connectedDebugAndroidTest --no-configuration-cache`，10 个 data instrumentation 测试通过；新增的三种预提交故障恢复、reconciliation 幂等和孤儿 pending 文件测试均通过。已执行 `clean test assembleDebug verifyModuleBoundaries --no-configuration-cache` 并通过；`git diff --check` 通过。emulator 控制台输出了既有 `Failed to start Emulator console for 5554` 提示，但 instrumentation 测试本身成功完成。

## Risks

文件和 Room 不是同一原子事务，恢复事务遗漏可能造成证据断链或 revision 重复；通过统一恢复提交事务、保留未知文件、诊断去重和故障注入测试降低风险。Bitmap 生命周期和跨模块依赖错误可能导致泄漏或循环依赖；使用 data-owned 输入并在完成后由调用方负责释放临时 Bitmap。

## Exit Criteria

Room schema 已导出且未配置 destructive fallback；实体关系、唯一段序列和外键可由数据库约束保证；原图按固定阶段提交并可 hash 去重；故障和启动 reconciliation 后原图及 slot/session/task/revision 元数据一致；未关联 pending 文件保留且有去重诊断；非终态 Session 可变为 INTERRUPTED 并人工恢复；Gradle 与 emulator instrumentation 门禁通过；P2 未扩展到明确的 Non-Scope。
