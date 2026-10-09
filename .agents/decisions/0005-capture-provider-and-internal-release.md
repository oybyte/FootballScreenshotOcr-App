# Decision: CaptureProvider 隔离平台能力，首发目标为 Internal APK

日期：2026-10-08
状态：Accepted
事实状态：Pending verification

最后核对：2026-10-09

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

`:app`/`:feature-capture` 已实现 P1 的权限入口、Accessibility/MediaProjection provider、悬浮 Service、图片导入和错误诊断；P1 预览仍只在内存中保留。MediaProjection 授权是一次会话状态，不能在进程重启后当作永久授权恢复。CaptureSession、provider 能力持久化、原图事务、进程恢复和 LongCapture 仍未实现，不能把本 Decision 当作完整能力已验证。

验证与退出条件：

当前 `clean test assembleDebug verifyModuleBoundaries` 和 `emulator-5554` 上的 8 个 instrumentation 测试已通过。仍需在该模拟器完成权限拒绝/撤销、投影停止与重新授权、图片选择器/系统分享、安全窗口、悬浮入口恢复和重复采集清理的手工矩阵。真实 OEM、后台冻结、进程强杀、旋转、锁屏、LongCapture、Play 政策审核和 live verified 保持 pending；发布记录必须区分 Internal 可用、Play 合规和 live verified。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 11、19、25 节
- `.agents/skills/long-capture/SKILL.md`
- `.agents/skills/release/SKILL.md`
- `.agents/plans/2026-10-08-p1-android-capture-spike.md`
