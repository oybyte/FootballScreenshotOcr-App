# Decision: Android/Windows 使用版本化 v7 契约

日期：2026-10-08
状态：Accepted
事实状态：Pending verification

背景：

Android 需要复用 Windows 的槽位、机构归一化、统计行、初盘/即盘、澳门时序、冲突、来源、证据、JSON、Markdown 和 prompt 语义，但不能运行 Python/PySide6 或依赖 Windows 路径。

问题：

如何让 Android 能独立开发，又不把新字段塞进 v6 或静默破坏跨端数据？

决定：

先建立可执行的 TaskManifest v7、CaptureSession v1、资源版本和脱敏 Golden fixture。v6 -> v7 保留已知字段并把旧数据的采集覆盖标为 `not_recorded`；v7 -> v6 不承诺无损降级，不得静默丢覆盖状态或会话关联。Android 只有在 Windows v7 读写/渲染和共同样本通过后，才能宣称跨端兼容。

原因：

版本化契约和共同 fixture 能把行为对齐从口头约定变成可测试边界，也能允许 Android Capture Spike 与 Windows 对接并行。

被拒绝的方案：

- 在 v6 JSON 中直接追加 Android 字段；
- 让 Windows 忽略未知字段后宣称兼容；
- 在 Android 和 Windows 各自维护一份字段真相；
- 没有真实 Windows consumer 时假定行为等价。

影响：

当前检出没有 Windows/Python、Schema、resources 或 Golden fixture，因此事实状态仍是 pending。契约任务必须先定位或引入这些来源，不能只创建 Kotlin 数据类。

验证与退出条件：

Schema 校验、v6 升级、v7 往返、未知枚举、冲突双向保留、bbox、赛果裁剪、Markdown 标题/行序和 Windows/Kotlin 对照全部有真实 fixture 和执行记录。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 2、9、10、17 节
- `.agents/skills/schema-change/SKILL.md`
