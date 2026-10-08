# LongCapture Skill

## 用途

实现或审查连续页面采集、悬浮采集、滚动控制、覆盖判断、段合并前的会话记录和恢复时使用。LongCapture 是 Android 版一级能力，优先级高于视觉装饰。

## 核心模型

LongCapture 是可暂停、可恢复、可诊断、可重放的会话，不是“连续截图”或一张巨大长图。至少保留：

```text
CaptureSession
  sessionId / taskId / slotId
  mode / provider / status
  viewport / insets / orientation
  segments[] / coverage / diagnostics

CaptureSegment
  segmentId / assetId / sequence
  timestamp
  scrollRequest
  observedMovement
  stabilityResult
  anchorSet
  overlapEvidence
  OCR reference
```

每张段图是独立 `ImageAsset`。逻辑页面坐标可以作为派生信息，但原图和原图归一化 bbox 永远是证据基准。

## 三种模式

### AUTO

应用请求滚动，等待页面稳定，再采集下一段。滚动请求不是实际位移；必须用滚动事件、容器状态、图像变化、锚点和相邻段匹配确认。

### FOLLOW

用户负责滑动：

```text
用户滑动 -> 页面停止 -> 应用自动采集 -> 用户继续滑动
```

FOLLOW 不能退化为点击一次后自动接管所有滚动。页面停稳检测、重复触发抑制、暂停和手动继续必须清楚可见。

### MANUAL

自动滚动不可用、权限被拒绝或页面不支持程序控制时，应用提示用户滑动和停稳，应用仍负责采集、持久化、OCR、合并和完整性诊断。MANUAL 是可靠降级路径，不是异常终止。

## 流程与状态

实现前先画出状态转换，至少覆盖：

```text
PREPARING
TOP_PENDING / TOP_CONFIRMED
CAPTURING
WAITING_STABLE
SEGMENT_COMMITTED
WAITING_USER (FOLLOW / MANUAL)
PAUSED
BOTTOM_PENDING / BOTTOM_CONFIRMED
REVIEW_REQUIRED
COMPLETED
FAILED
```

每个状态必须定义进入条件、退出条件、超时、持久化点和用户可见诊断。进程被杀后从持久化状态恢复，不以 Service 内存为事实来源。

## 稳定、顶部和底部

- 稳定检测不能只 `sleep(500)`，也不能只比较整屏像素；要屏蔽状态栏、固定 Header、悬浮控件和动态区域，并结合内容位移。
- 开始采集必须确认顶部；当前位置未知时不能假设已经在顶部。
- 到底必须由滚动结果、事件、内容位移、锚点、重复区域和页面结构综合判断。
- 连续两帧相同只能证明短暂稳定，不能单独证明顶部、底部或覆盖完整。

## Overlap、Anchor 与 Coverage

65%–75% 视口位移只是请求目标，不是 overlap 保证。Coverage 依据实际匹配证据计算：

```text
top_confirmed
bottom_confirmed
adjacent_segments_connected
gap_count
overlap evidence
anchor evidence
```

只允许以下覆盖状态：

```text
NOT_APPLICABLE
NOT_RECORDED
COMPLETE
LIKELY_COMPLETE
GAP_SUSPECTED
INCOMPLETE
```

只有顶部和底部确认、所有相邻段连接、无未解释 gap 时才允许 `COMPLETE`。`LIKELY_COMPLETE` 必须进入人工核对并保留告警；`GAP_SUSPECTED` 和 `INCOMPLETE` 禁止自动完成槽位。

固定 Header 不得重复进入业务数据。吸附滚动、惯性滚动、虚拟列表、动态广告和内容刷新必须在合成测试中覆盖。

## Capture 与 OCR

严格遵守：

```text
Capture -> 原图/段元数据持久化 -> OCR Queue
```

采集不等待完整 OCR 才继续。原图保存、哈希/可读性校验和提交状态必须先完成；OCR 失败也不删除段图。段合并和字段完整性由后续 Parser/Validator 负责。

## 熔断与 Replay

会话必须有：

```text
maxSegments
maxScrollAttempts
maxNoProgress
maxStabilityWait
maxSessionDuration
```

持续无进展、稳定超时、权限丢失、存储失败或观察位移异常时停止并进入 `REVIEW_REQUIRED`，不得无限重试。保存 replay：段序、滚动请求、实际位移、稳定结论、锚点、coverage 决定和诊断，使 coverage bug 可以离线重放。

## 验证

先做不接 OCR 的采集合成测试，再接真实设备。测试至少包含：缺中段、无锚点、重复段、固定 Header、未知顶部、未知底部、吸附位移、动态区域、用户暂停、恢复、权限拒绝、进程强杀、低存储和自动滚动失败降级。硬门槛是注入任意中间缺段或未确认顶部/底部时 false-complete 为 0。
