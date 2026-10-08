# Storage Migration Skill

## 用途

实现或审查 Room schema、任务事实、文件提交、DataStore、历史 revision、恢复、reconciliation 和崩溃测试时使用。任何 T5 变更都必须建立 ExecPlan。

## 数据职责

目标架构的职责固定为：

```text
Room      运行时任务事实源与可查询元数据
Files     原图、revision、导出物和训练归档
DataStore 轻量用户设置
```

Room 保存 Task、FixtureIdentity、Slot、ImageAsset 元数据、CaptureSession/Segment 元数据、OCR 状态和诊断、Conflict、Revision 以及实际资源版本。Markdown/JSON 是派生输出，不是第二任务数据库。DataStore 不得保存任务真相、OCR 结果、完成状态或采集段。

当前仓库还没有 Room；在真正引入前，必须以 Decision 和 Plan 明确初始化 schema、迁移策略、文件关系和恢复边界，不能把目标架构误写成现状。

## Room Migration

- 开启 schema export，把每个版本提交到可审计位置。
- 每次 schema 变化提供明确 Migration 和旧数据 fixture。
- 禁止 `fallbackToDestructiveMigration`。
- 不删除无法迁移的原始证据；无法解释的数据进入诊断/人工核对。
- 验证旧版本升级、重复升级、空数据库、部分事务和进程被杀。

## 文件提交事务

Room 事务和普通文件复制/重命名不是一个原子事务。采用可恢复的阶段：

```text
pending
  -> 写 staging 文件
  -> 校验可读性、大小和 hash
  -> 原子重命名到正式路径
  -> 写/更新 Room 记录
  -> 标记 committed
  -> reconciliation
```

原图必须先可靠保存，不能放入 Room BLOB 作为唯一事实。失败时保留可恢复文件和诊断；启动时处理临时文件、清单引用缺图、孤儿图片、未提交会话和已提交但未生成导出的状态。

## 恢复与 Reconciliation

启动恢复必须能区分：

- Room pending 但文件存在；
- 文件已提交但 Room 记录缺失；
- Room 引用缺图；
- 未完成 CaptureSession；
- 重复 asset/hash；
- 低存储或权限失败。

修复动作要幂等、可解释、可追溯。不得自动猜测缺失图片对应的任务，不得静默删除孤儿原图；需要用户裁决时进入 `REVIEW_REQUIRED`。

## DataStore

DataStore 仅保存主题、悬浮球位置、最近采集模式、用户偏好等轻量设置。它不是任务数据库，也不能成为 Room 失败后的静默 fallback。

## 测试

至少执行：migration fixture、事务阶段强杀、重启恢复、低磁盘、重复提交、写入中断、数据库清空后的重建、孤儿文件 reconciliation 和 revision 保留。测试失败先分析代码/测试/环境/假设，不通过删除数据或降低门槛“修复”。
