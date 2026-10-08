# Schema Change Skill

## 用途

任何 TaskManifest、CaptureSession、Slot、Evidence、Conflict、输出 JSON/Markdown 语义或版本化 Contract 的增加、删除、重命名和枚举变化都必须使用本 Skill，并按 T4/T6 验证。

## 先确认事实

1. 读取当前 Schema、序列化模型、Renderer、迁移代码和 fixtures。
2. 搜索 Windows consumer、Android consumer、资源版本和历史样本。
3. 确认当前版本、未知字段策略、null/缺省语义和输出顺序。
4. 如果仓库没有 Windows 实现、Schema 或 fixture，标记为 `pending`，先建立来源清单，不能宣称兼容。
5. 建立 ExecPlan；影响架构时建立/更新 Decision。

## v6 -> v7 边界

Android 目标契约为 v7。v7 必须保留既有 v6 字段语义，并为采集来源、覆盖状态、会话引用、诊断和证据关联提供明确结构。不得把 LongCapture 字段塞入仍标为 v6 的 JSON。

迁移规则：

```text
v6 -> v7：保留已知字段；来源标 legacy；覆盖状态标 not_recorded
v7 -> v6：不承诺无损降级；不得静默丢弃新状态或会话关联
```

如果 Windows v7 读写/渲染尚未存在，Android 可以在明确的 P0A 阶段保存本地 v7，但不能称为跨端互操作完成。迁移必须保留 `outcome_history`、`prematch_snapshot`、`archive_state`、`replacement_history` 和全部槽位语义。

## 修改要求

- 先写 Schema/Contract，再改 Kotlin/Python 模型、Parser、Renderer 和存储。
- 字段类型、枚举、必填项、默认值、未知值处理、坐标范围、版本和兼容策略必须可执行。
- 新字段不能用“Android 自己理解”替代共享契约。
- 冲突双方、asset id、原图 bbox、coverage 诊断必须可序列化且可恢复。
- 结构变化必须增加版本并提供明确迁移；禁止隐式兼容和静默丢字段。

## Golden fixture

每个 Schema 变更至少添加或更新脱敏 fixture，覆盖：

- v6 升级和 v7 往返序列化；
- 缺省与显式空值；
- 未知枚举/字段；
- 多槽位与澳门时序；
- 冲突双向保留；
- bbox 与逻辑坐标映射；
- 赛果裁剪、Markdown 标题和行序；
- `replacement_history` 和资源版本。

不得为了绿色测试修改期望值或删除旧样本。期望输出变化必须由 Decision、迁移说明和版本变更解释。

## 验证

最低门禁包括 JSON Schema 校验、旧样本迁移、往返序列化、Windows/Kotlin 对照、Renderer 输出差异、未知字段策略和数据完整性。实际未执行的验证必须标 `pending`。Schema 改动完成前，不得让业务代码在多个模块里各自定义一份字段真相。
