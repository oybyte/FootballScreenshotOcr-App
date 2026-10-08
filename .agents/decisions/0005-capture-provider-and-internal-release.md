# Decision: CaptureProvider 隔离平台能力，首发目标为 Internal APK

日期：2026-10-08
状态：Accepted
事实状态：Planned

背景：

AccessibilityService 适合验证截图、节点滚动和事件跟随，但 Google Play 对非无障碍工具使用 Accessibility API 有申报、披露、同意和审核要求。MediaProjection 能采集显示内容，但不负责滚动；权限、厂商后台限制和投影撤销也会影响可用性。

问题：

如何先验证完整的悬浮采集与 LongCapture，同时避免把某个 Android API 写死进业务层或把 Internal 能力误报为 Play 保证？

决定：

业务层只依赖 `CaptureProvider`。首发验证目标为 Internal APK，优先验证 Accessibility + Overlay + AUTO/FOLLOW/MANUAL，并保留 MediaProjection、手动跟随、相册和分享导入降级。Play 路线必须经过独立政策审核，默认不依赖 Accessibility 自动滚动。

原因：

Provider 隔离允许替换平台实现而不复制业务逻辑；Internal APK 能快速验证核心采集可靠性，手动/导入路径保证权限或平台能力失败时不丢数据。

被拒绝的方案：

- 让 LongCapture 直接依赖 AccessibilityService 或 MediaProjection；
- 把 MediaProjection 当作滚动器；
- 没有审核结论就宣称 Play 支持自动滚动；
- 权限拒绝后把会话标成成功或丢弃已采集段。

影响：

需要在 `:app`/capture feature 中管理权限、Service、Provider 生命周期，并持久化每次会话的 provider 和能力状态。当前 Manifest 尚无这些能力，不能把本 Decision 当作已实现。

验证与退出条件：

在代表设备上验证权限拒绝、投影撤销、后台冻结、进程强杀、旋转、锁屏、手动降级和恢复；发布记录必须区分 Internal 可用、Play 合规和 live verified。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 11、19、25 节
- `.agents/skills/long-capture/SKILL.md`
- `.agents/skills/release/SKILL.md`
