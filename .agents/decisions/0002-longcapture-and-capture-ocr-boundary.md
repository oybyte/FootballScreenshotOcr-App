# Decision: LongCapture 作为一级能力并与 OCR 解耦

日期：2026-10-08
状态：Accepted
事实状态：Planned

背景：

盘口机构列表和澳门时序可能需要多次滚动。单屏 OCR 无法证明页面完整，长页面还会遇到惯性滚动、固定 Header、动态区域和权限限制。

问题：

如何采集连续页面，避免漏段、伪完整和巨大 Bitmap，同时让 OCR 失败不阻塞采集或丢失原图？

决定：

LongCapture 是一级能力，提供 `AUTO`、`FOLLOW`、`MANUAL` 三种模式。每次采集保存独立 `CaptureSegment` 和 replay 信息；使用稳定、顶部、底部、overlap、anchor、gap、Coverage 和 circuit breaker 判定状态。流程固定为 `Capture -> Segment 持久化 -> OCR Queue`。

原因：

分段原图和可重放诊断能支持恢复、证据定位和离线复现。FOLLOW 保留用户控制权，MANUAL 提供平台能力失败时的可靠降级。Coverage 以证据而非段数为依据。

被拒绝的方案：

- 固定滑动 700px 作为完整性证明；
- 未确认顶部/底部或有 gap 时输出 COMPLETE；
- 把所有截图拼成一张巨大 Bitmap；
- 让完整 OCR 阻塞滚动或把 OCR 成功作为保留原图条件。

影响：

需要 `CaptureSession`、`CaptureSegment`、状态机、熔断和 replay 持久化；当前仓库没有这些实现。Coverage `COMPLETE` 仍需通过数据完整性门禁才可完成槽位。

验证与退出条件：

合成测试对任意中间缺段、未知顶部或未知底部不得 false-complete；设备测试覆盖 AUTO/FOLLOW/MANUAL、暂停恢复、权限拒绝、进程强杀和低存储。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 2、11、13 节
- `.agents/skills/long-capture/SKILL.md`
