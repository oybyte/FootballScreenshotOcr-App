# FootballScreenshotOcr Android 版详细开发步骤 v2.0

> 文档用途：作为 Android 工程实施顺序、阶段交付、接口边界和验收标准的执行基线。
>
> 业务规则以 Windows 现役产品规则、跨端契约和本文件约束共同决定；Android 不重新定义盘口解析语义。
>
> 适用工程：`D:\as-workplace-tly-2026\FootballScreenshotOcr`
>
> 版本：2.0  
> 更新日期：2026-10-08  
> 首版发布目标：个人 / 内部 APK，不以 Google Play 上架为目标

---

## 1. 产品定位与首版边界

### 1.1 产品定位

Android 版不是 Windows 桌面版的机械移植，也不是传统图片 OCR 工具，而是：

> **运行在盘口 App 旁边的本地盘口采集副驾。**

核心工作流：

```text
新球体育 / 目标盘口 App
        ↓
悬浮入口
        ↓
当前屏幕 / 长页面 / 跟随采集 / 相册 / 分享
        ↓
截图 Segment
        ↓
页面完整性分析
        ↓
OCR
        ↓
图片判型
        ↓
Slot 路由
        ↓
多 Segment 合并
        ↓
字段完整性 / 冲突 / 证据核对
        ↓
Task
        ↓
Markdown + JSON
```

### 1.2 首版交付范围

首版必须完成：

- 悬浮入口
- 当前屏幕单次采集
- LongCapture 自动模式
- LongCapture FOLLOW 跟随模式
- LongCapture MANUAL 手动模式
- 相册批量导入
- Android Sharesheet 分享导入
- 自动图片判型
- 自动 Slot 路由
- Segment 持久化
- Segment 合并
- Coverage / Completeness 检查
- OCR
- 原图证据查看
- 冲突核对
- Room 任务存储
- Markdown / JSON 输出
- 体彩官方查询 + 500.com 兜底回填
- 任务恢复和异常恢复
- 内部 release APK

### 1.3 首版明确不做

```text
不做比赛预测
不做投注建议
不做在线盘口分析
不做账号体系
不做云同步
不做云数据库
不做多用户
不做 AI 推理
不做体彩高级玩法 UI
不做在线 OCR
不做运行时下载 OCR 模型
不做 Google Play 专用发布流程
```

### 1.4 数据安全边界

盘口图片、OCR、解析和任务数据默认全部在本机处理和保存。

体彩期号 / 主队让球自动回填是明确的联网例外，仅允许向体彩官方和 500.com 发送完成赛事身份查询所需的数据；禁止发送截图、盘口明细、任务 ID、prompt 和 OCR 结果。

因此产品说明使用：

> **“本地处理，少量赛事身份查询可联网。”**

不得写成“完全离线”。

---

# 2. 不可改变的业务不变量

现有项目把 `Task` 作为一场比赛的一次赛前采集，并包含比赛身份、8 个槽位、赛果、赛前快照、归档状态和替换历史。fileciteturn0file0L70-L84

## 2.1 八个槽位

| Slot | 名称 | 属性 |
|---|---|---|
| 1 | 让球盘 | 核心 |
| 2 | 让球盘澳门详细变化 | 补充 |
| 3 | 胜平负 | 核心 |
| 4 | 胜平负澳门详细变化 | 补充 |
| 5 | 总进球 | 核心 |
| 6 | 总进球澳门详细变化 | 补充 |
| 7 | 凯利 | 核心 |
| 8 | 必发交易盈亏 | 数据层保留，UI 隐藏 |

四个核心槽位完成且比赛身份确认后自动生成文档。fileciteturn0file0L86-L104

## 2.2 不能被 Android 交互破坏的规则

```text
不猜测未知字段
不自动二选一解决冲突
不静默覆盖历史数据
不静默截断机构
不删除已有输出
手工修改后必须更新来源语义
OCR 告警必须保留
证据必须可以回溯
```

现有产品明确要求“宁缺毋滥”、来源诚实、冲突保留和原图证据链。fileciteturn0file0L284-L293 fileciteturn0file0L133-L143

---

# 3. 总体技术架构

```text
┌────────────────────────────────────────────┐
│                    :app                    │
│ Compose / Navigation / Overlay / Service │
│ Permission / Share / Android integration  │
└──────────────────────┬─────────────────────┘
                       │
       ┌───────────────┼────────────────┐
       ↓               ↓                ↓
 :core-domain   :core-capture      :core-storage
       │               │                │
       └───────┬───────┴───────┬────────┘
               ↓               ↓
          :core-ocr       :core-parser
               │               │
               └───────┬───────┘
                       ↓
                 Common Contract
                       ↓
              Markdown / JSON / Revision
```

## 3.1 Module 依赖

| Module | 职责 | 允许依赖 |
|---|---|---|
| `:app` | Compose、导航、Overlay、Service、权限和 Android 装配 | 全部 core |
| `:core-domain` | Task、Slot、Identity、Evidence、状态、不变量 | 无 Android UI |
| `:core-capture` | 单屏采集、LongCapture、滚动、稳定、Segment、Coverage | `:core-domain` |
| `:core-ocr` | ONNX Runtime、图像预处理、OCR Queue、坐标映射 | `:core-domain` |
| `:core-parser` | 判型、Layout、SlotRouter、Parser、Merge、Validator | `:core-domain` |
| `:core-storage` | Room、文件、事务、恢复、导出快照 | `:core-domain` |
| `:core-contract` | v7 schema、枚举、fixture contract | 无 Android |
| `:core-network` | 体彩 resolver、网络诊断 | `:core-domain` |

原则：

- `Composable` 不直接访问 DAO。
- `Activity` 不写业务解析。
- `Service` 不直接实现 Parser。
- `core` 不反向依赖 `:app`。
- 网络实现必须可替换为 Fake。

---

# 4. P0A：工程基线与跨端契约冻结

## 4.1 建立工程基线

### 步骤

1. 在工程根目录检查 Git branch、status、本地修改、Gradle wrapper、Android SDK 和 JDK。
2. 记录当前 commit、工作树变化和现有 Android 示例模块状态。
3. 执行：

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

4. 初始失败必须区分为：
   - 环境问题
   - 原有模板问题
   - 新改动问题
5. 不覆盖用户已有工作区修改。
6. 建立 Gradle Version Catalog。
7. 建立模块骨架和每个模块的一条边界测试。

### SDK 基线

首版按当前工程决定：

```text
minSdk = 30
compileSdk = 36
targetSdk = 36
```

Android 16 对应 API 36；Google Play 目前要求自 2026-08-31 起新 App 和更新以 API 36 或更高为 target，但本项目首版不以上架 Play 为目标。citeturn672839search7

JDK / AGP / Kotlin 版本以仓库实际 Gradle 组合的兼容矩阵为准，不在 P0 无依据地升级其它工具链。

## 4.2 v7 Schema Contract

Android 不直接把现有 v6 当成最终跨端格式。

建立：

```text
contracts/
├── task-manifest-v7.schema.json
├── capture-session-v1.schema.json
├── resource-manifest.schema.json
└── README.md
```

### v7 必须覆盖

- v6 已有全部字段
- Task / Fixture / Slot
- ImageAsset
- CaptureSession
- CaptureSegment
- Coverage
- Diagnostics
- Evidence
- PrematchSnapshot
- OutcomeHistory
- ArchiveState
- ReplacementHistory
- ResourceVersion

### 证据规则

所有截图派生字段：

```text
Asset ID
+
原图归一化 bbox
+
OCR / Parser 版本
```

非截图字段：

```text
SourceReference
```

例如：

```text
official_sporttery
lottery500
manual
system_generated
```

不能要求赛事期号等网络/人工字段必须具有 OCR bbox。

## 4.3 v6 → v7

兼容策略：

```text
v6
 ↓
importer
 ↓
v7
```

旧数据：

```text
coverage = not_recorded
capture_session = legacy / null
```

未知字段不能静默删除已知信息。

v7 → v6：

> 不保证无损；禁止静默丢失 Coverage、Session、诊断和证据关系。

## 4.4 Windows v7 支持

Windows v7 读写和 renderer 必须最终通过共同 Golden Fixtures。

但是：

> **Windows v7 实现不是 P1～P5 Android 开发的硬阻塞项。**

阶段划分：

```text
P0A：冻结契约
P0B：Windows 支持 v7
```

P0B 可以与 P1～P4 并行，但在 Android 宣称“跨端互操作”之前必须通过。

## 4.5 P0A 出口

- Android 根工程可构建。
- 模块依赖无环。
- v7 schema 有 JSON Schema 测试。
- v6 importer 有 Golden Tests。
- 资源有版本、来源、SHA-256。
- 不读取开发机绝对路径。
- P0B 未通过时，不宣称 Android v7 已完成 Windows 互操作。

---

# 5. P0B：Windows v7 对接与共同 Golden Fixtures

## 5.1 目标

Windows 不是 Android 的前置阻塞，但必须成为 Android 输出的行为参照。

## 5.2 Golden Fixture

目录：

```text
contracts/golden/
├── case-normal/
├── case-conflict/
├── case-timeline/
├── case-stat-row/
├── case-manual-edit/
├── case-outcome/
└── case-v6-migration/
```

每个案例至少包含：

```text
input.json
expected-v7.json
expected.md
```

必要时增加：

```text
expected-diagnostics.json
```

## 5.3 Fixture 类型

必须包括：

- 普通让球
- 多机构
- 澳门时序
- 重复 Segment
- 同时间不同值
- 统计行
- 未知机构
- 空值 / null
- 手工修改
- 替换历史
- 赛果写入
- 赛前 prompt 排除赛果
- 证据 bbox
- v6 → v7

## 5.4 P0B 出口

Windows 能读取和写入 v7；Android 后续必须用同一 fixture 做差异比较。

---

# 6. P1：设备、权限、悬浮层和 Capture Spike

P1 的目标不是做产品 UI，而是回答：

> **真实手机上能否稳定获得“当前屏幕”以及稳定进行页面滚动。**

Jev 当前 Android 项目已经采用悬浮窗、无障碍采集和 OCR 兜底；其实际版本也暴露了国产 ROM 后台冻结、受保护窗口无法截图和只能读取当前可见区域等问题，因此这些都必须前置实机验证。citeturn672839search1turn672839search0

## 6.1 设备矩阵

至少：

```text
Pixel / AOSP
Samsung / 同类标准 Android
Xiaomi / HyperOS 或等效强限制 ROM
```

覆盖：

```text
API 30
API 34
API 36
```

## 6.2 Accessibility 能力 Spike

最小 Service：

```text
canRetrieveWindowContent
canTakeScreenshot
canPerformGestures
```

验证：

- 开启
- 撤销
- 重授权
- 服务被系统停止
- 重启后恢复
- Overlay 同时存在
- 截图时隐藏 Overlay

Android API 30 起提供 AccessibilityService 截图能力；AccessibilityNodeInfo 支持前进/后退滚动动作，可作为滚动控制的优先 provider。citeturn672839search1

## 6.3 Overlay Spike

实现：

```text
小型悬浮球
→ 点击
→ 底部采集面板
```

面板只显示：

```text
当前任务
核心进度
当前页面采集状态
当前屏幕
长页面
导入图片
```

不实现完整任务管理 UI。

## 6.4 Capture Provider

定义：

```kotlin
interface CaptureProvider {
    suspend fun capture(request: CaptureRequest): CaptureResult
}
```

实现：

```text
AccessibilityCaptureProvider
MediaProjectionCaptureProvider
GalleryCaptureProvider
ShareIntentCaptureProvider
ManualCaptureProvider
```

### 优先级

```text
Accessibility Screenshot
        ↓失败
MediaProjection（仅截图）
        ↓
Manual Follow
        ↓
Gallery / Share
```

MediaProjection 不负责自动滚动。

Android 14+ 对 MediaProjection 每个 capture session 的用户授权及前台服务类型有额外要求，因此它必须作为独立 provider，不得成为 LongCapture 生命周期的上层依赖。citeturn672839search1

## 6.5 FLAG_SECURE

必须专门测试：

```text
受保护窗口 / FLAG_SECURE
```

失败统一记录：

```text
CAPTURE_BLOCKED_BY_WINDOW_SECURITY
```

不得伪装成截图成功。

## 6.6 P1 出口

至少满足：

```text
一种实时截图路径可用
一种图片导入路径可用
```

自动滚动不达标不能阻塞后续，因为 FOLLOW / MANUAL 必须可用。

---

# 7. P2：Room、文件提交、恢复和数据一致性

## 7.1 Room 是唯一任务事实源

Room 保存：

```text
Task
FixtureIdentity
Slot
ImageAsset metadata
CaptureSession
CaptureSegment metadata
OCR state
Diagnostics
Coverage
Conflict
Revision
ResourceVersion
```

大图和 OCR 中间 Bitmap 不放 SQLite BLOB。

## 7.2 DataStore

只保存：

```text
主题
悬浮球位置
最近采集模式
轻量 UI 偏好
```

不能保存任务事实。

## 7.3 原图提交协议

```text
Room pending
   ↓
临时文件
   ↓
可解码检查
   ↓
尺寸 / 字节检查
   ↓
SHA-256
   ↓
原子 rename
   ↓
Room committed
```

任何一步失败：

```text
保留原图 / temp
记录诊断
禁止 complete
```

## 7.4 启动 reconciliation

启动检查：

```text
Room pending without file
file without Room reference
temp file
damaged file
```

处理：

```text
可恢复 → 恢复
无法恢复 → 进入诊断
```

不得静默删除唯一原图。

## 7.5 崩溃注入

在以下位置模拟强杀：

```text
pending 创建之后
文件写完之后
rename 之后
commit 之前
commit 之后
OCR enqueue 之后
```

目标：

> 重启之后不存在“看似完整、实际上缺图”的任务。

## 7.6 备份策略

首版明确：

```text
不提供应用内备份/恢复
关闭 Android 自动备份
```

用户必须看到：

> 卸载、清除数据、设备损坏后可能永久丢失本机数据。

---

# 8. P3：LongCapture 核心引擎

这是 Android 版最重要的阶段。

目标不是“拼出一张长图”，而是：

> **连续采集一个滚动页面，并证明中间没有被遗漏。**

## 8.1 LongCapture 模式

```text
AUTO
FOLLOW
MANUAL
```

### AUTO

程序控制：

```text
回顶部
→ 截图
→ 滚动
→ 等稳定
→ 截图
→ …
→ 到底确认
```

### FOLLOW

用户控制滚动：

```text
用户上滑
→ 停
→ 页面稳定
→ 自动截图
→ 用户继续上滑
→ 停
→ 自动截图
```

### MANUAL

用户点击：

```text
[继续采集]
```

程序采集当前稳定屏幕。

## 8.2 Session 状态机

```text
CREATED
 ↓
PREPARING
 ↓
TOP_CHECKING
 ↓
CAPTURING
 ↓
WAITING_STABLE
 ↓
SEGMENT_COMMITTED
 ↓
SCROLLING
 ↓
WAITING_STABLE
 ↓
SEGMENT_COMMITTED
 ↓
...
 ↓
BOTTOM_CHECKING
 ↓
COVERAGE_CHECKING
 ↓
COMPLETE
```

异常状态：

```text
PAUSED
INTERRUPTED
REVIEW_REQUIRED
SCROLL_BLOCKED
CAPTURE_BLOCKED
STABILITY_TIMEOUT
TOP_UNKNOWN
BOTTOM_UNKNOWN
GAP_SUSPECTED
FAILED
```

非法状态转换必须有测试。

## 8.3 Segment 数据结构

```text
CaptureSegment
├── segmentId
├── taskId
├── slotId
├── sequence
├── assetId
├── capturedAt
├── orientation
├── viewport
├── insets
├── scrollBeforeObservation
├── scrollAfterObservation
├── stableAt
├── anchorSet
├── coverageEvidence
└── diagnostics
```

## 8.4 采集与 OCR 解耦

原则：

```text
Capture
 ↓
持久化 Segment
 ↓
Coverage / Anchor lightweight analysis
 ↓
OCR Queue
```

不要让完整 OCR 阻塞滚动采集。

除非后续验证证明某些页面必须依赖 OCR anchor，否则默认先完成采集，再异步 OCR。

---

# 9. P3：滚动控制

## 9.1 ScrollController 抽象

```kotlin
interface ScrollController {
    suspend fun prepare(): ScrollPrepareResult
    suspend fun moveToTop(): ScrollResult
    suspend fun scrollForward(amount: Float): ScrollResult
    suspend fun stop(): ScrollResult
}
```

实现：

```text
AccessibilityNodeScroller
GestureScroller
ManualFollowScroller
```

## 9.2 滚动优先级

```text
1. Accessibility Node Scroll
2. Accessibility Gesture
3. FOLLOW / MANUAL
```

不能固定：

```text
“每次上滑 700 px = 页面移动 700 px”
```

请求滚动距离只是控制参数；实际 coverage 必须由滚动事件、截图内容变化和 anchor 验证。

## 9.3 顶部确认

必须：

```text
MOVE_TO_TOP
→ VERIFY_TOP
```

无法自动确认时：

```text
请滑到顶部
[已到顶部，开始采集]
```

不能默认当前页面就是顶部。

## 9.4 稳定检测

不能固定使用：

```text
sleep(500)
```

而要组合：

```text
页面内容差异
滚动事件
内容锚点
系统动画状态
```

状态：

```text
MOVING
STABILIZING
STABLE
TIMEOUT
```

连续两帧相同不能单独作为稳定证据。

## 9.5 动态区域

默认可 mask：

```text
状态栏
已经确认的固定 Header
已经确认的悬浮控件
```

未知动态区域不能自动 mask。

如果广告覆盖盘口内容，则该区域应进入：

```text
UNKNOWN
```

并可能导致 REVIEW_REQUIRED。

---

# 10. P3：重叠、Anchor 和 Coverage

## 10.1 Overlap

初始策略：

```text
每次内容位移约 65%～75%
保留约 25%～35% overlap
```

实际值通过真机数据校准，不写死业务假设。

Overlap 的作用不是“拼图美观”，而是证明：

> 前后 Segment 之间确实连续。

## 10.2 AnchorTracker

优先寻找：

```text
机构名
澳门
盘口
时间
表头
稳定行
```

例如：

```text
S1:
Bet365
Pinnacle
Betway

S2:
Betway
WilliamHill
Interwetten
```

`Betway` 是连接锚点。

如果相邻 Segment 没有足够可证明的 overlap：

```text
GAP_SUSPECTED
```

而不是继续伪装为完整。

## 10.3 CoverageEvidence

```text
CoverageEvidence
├── topConfirmed
├── bottomConfirmed
├── adjacentSegmentsConnected
├── minOverlap
├── gapCount
├── gapRegions[]
├── duplicateRegions[]
├── scrollObservations[]
└── endEvidence
```

## 10.4 Coverage 状态

```text
NOT_APPLICABLE
NOT_RECORDED
COMPLETE
LIKELY_COMPLETE
GAP_SUSPECTED
INCOMPLETE
```

### COMPLETE 条件

必须同时满足：

```text
topConfirmed == true
bottomConfirmed == true
adjacentSegmentsConnected == true
gapCount == 0
```

无法满足任一项：

```text
不是 COMPLETE
```

未知页长时不显示“96%”这类伪精确 coverage。

---

# 11. P3：长页面熔断与恢复

必须避免：

```text
页面没动
→ 程序继续滚
→ 无限采集
```

配置：

```text
maxSegments
maxScrollAttempts
maxNoProgress
maxStabilityWait
maxSessionDuration
```

例如策略：

```text
连续多次滚动没有新的内容位移
→ STOP
→ REVIEW_REQUIRED
```

阈值必须在真机 Spike 中校准，不在文档中虚构固定数字。

## 11.1 暂停

自动模式随时：

```text
[暂停]
```

暂停后允许用户：

```text
手动调整页面
```

然后：

```text
[继续]
```

系统重新建立 overlap / anchor。

## 11.2 中断恢复

可能原因：

```text
App 切后台
服务被停止
权限撤销
设备锁屏
进程被杀
```

重启后：

```text
CaptureSession = INTERRUPTED
```

用户可以：

```text
继续
补采
结束为部分结果
```

---

# 12. P3：逻辑长图，不生成巨大 Bitmap

禁止以：

```text
1440 × 30000 巨大 Bitmap
```

作为唯一内部表示。

内部使用：

```text
LongCapture
├── S001
├── S002
├── S003
└── S004
```

每个 Segment 保存原始 Asset。

建立逻辑坐标：

```text
segment local bbox
        ↓
logical page coordinate
        ↓
original asset bbox
```

例如：

```json
{
  "value": "0.91",
  "segment_id": "S003",
  "asset_id": "A003",
  "bbox": [0.12, 0.21, 0.17, 0.24],
  "logical_rect": [123, 1837, 175, 1865]
}
```

这样仍然保留原图证据链。

---

# 13. P3：固定 Header / Sticky Header

页面抽象：

```text
Page
├── FixedRegion
└── ScrollRegion
    ├── S001
    ├── S002
    └── S003
```

固定 Header：

```text
用于上下文
但不能作为每个 Segment 的数据行重复合并
```

ScrollRegion 才是 LongCapture 主要 coverage 对象。

---

# 14. P3：数据完整性分析

Coverage 只回答：

> 页面有没有采全？

DataCompletenessAnalyzer 回答：

> 数据有没有解析全？

检查：

```text
机构数量
统计行
表头
列结构
时序连续性
重复行
冲突
```

## 14.1 双层完整性

```text
L0 Capture
├── Top
├── Bottom
├── Overlap
└── Gap

L1 Data
├── Provider
├── Timeline
├── Fields
└── Diagnostics
```

最终：

```text
L0 COMPLETE
+
L1 满足业务门禁
=
Slot 可进入自动生成链路
```

---

# 15. P4：OCR Spike 与模型冻结

Jev 实际版本证明了“本机 OCR + 无障碍截图”在 Android 上可以工作，但其 OCR 仍只处理当前可见区域；本项目需要解决的是多 Segment 连续采集后的 OCR，而非只做单屏 OCR。citeturn672839search0

## 15.1 模型资产

建立：

```text
models/
├── detector.onnx
├── recognizer.onnx
└── manifest.json
```

manifest：

```text
model version
source
license
file size
sha256
preprocess version
```

运行时禁止在线下载替代模型。

## 15.2 OCR Runtime

首版：

```text
ONNX Runtime Android
CPU baseline
```

GPU / 设备加速仅做后续 Spike。

不要把 NNAPI 作为新的架构依赖；Android 官方已将 NNAPI 标记为 deprecated。citeturn672839search7

## 15.3 OCR Queue

```text
Queue<OcrJob>
```

状态：

```text
WAITING
RUNNING
SUCCEEDED
FAILED
RETRY
CANCELLED
```

首版默认单 Worker。

## 15.4 OCR Job 幂等

Job 必须携带：

```text
Task ID
Slot ID
Session ID
Segment ID
Asset ID
modelVersion
resourceVersion
```

重复执行不会重复生成业务结果。

## 15.5 原始 OCR Token

OCR 输出必须保留：

```text
raw text
confidence
bbox
model version
```

Parser 可以产生规范化值，但不能先把原始 token 抹掉。

---

# 16. P5：ImageClassifier 与 SlotRouter

## 16.1 ImageClassifier

输出：

```text
HANDICAP
HANDICAP_MACAU
EURO
EURO_MACAU
TOTAL_GOALS
TOTAL_GOALS_MACAU
KELLY
UNKNOWN
```

依据：

```text
版式
表头
关键词
机构结构
盘口结构
```

判型不足：

```text
UNKNOWN
```

不强行猜测。

## 16.2 SlotRouter

```text
HANDICAP          → Slot 1
HANDICAP_MACAU    → Slot 2
EURO              → Slot 3
EURO_MACAU        → Slot 4
TOTAL_GOALS       → Slot 5
TOTAL_GOALS_MACAU → Slot 6
KELLY             → Slot 7
```

Slot 8 保留在数据层。

## 16.3 非空 Slot

发现目标 Slot 已经有数据：

```text
[补充]
[替换]
[取消]
```

禁止静默覆盖。

Slot 容量规则来自 `SlotDefinition`，不要写死在代码中。

---

# 17. P5：SegmentMerger

## 17.1 机构合并

```text
S1: A B C D
S2: C D E F
```

输出：

```text
A B C D E F
```

同时保留：

```text
C → S1 source
C → S2 source
```

## 17.2 同值重复

```text
同一行
同一 phase / time
值相同
```

→ 业务结果去重，但证据源全部保留。

## 17.3 冲突

```text
同一 phase / time
值不同
```

→ `CONFLICT`

必须保留：

```text
左值
右值
source asset
bbox
```

不得自动二选一。

---

# 18. P5：TimelineMerger

澳门时序单独处理。

例如：

```text
S1
10:30
11:20
12:35

S2
12:35
13:20
14:10
```

最终：

```text
10:30
11:20
12:35
13:20
14:10
```

`12:35` 的重复证据必须保留。

同一时间不同值：

```text
CONFLICT
```

不能因为“后一个 Segment 更新”而覆盖前一个。

---

# 19. P5：FixtureIdentity 与体彩回填

## 19.1 身份模型

核心：

```text
赛事
主队
客队
开赛时间
体彩期号
主队让球
```

自动匹配必须遵守 Windows 现有的精确别名与唯一性规则。现有产品明确不使用模糊匹配、不造赛程 ID、不补造开赛时间。fileciteturn0file0L145-L155

## 19.2 Resolver 接口

```kotlin
interface FixtureResolver {
    suspend fun resolve(identity: FixtureIdentity): ResolverResult
}
```

实现：

```text
OfficialSportteryResolver
Lottery500Resolver
FakeFixtureResolver
```

Parser 不直接访问网络。

## 19.3 网络边界

只发送：

```text
必要队名
日期 / 销售日
必要赛事身份查询字段
```

禁止发送：

```text
截图
盘口数据
OCR 文本
任务 ID
prompt
```

## 19.4 官方源优先

```text
体彩官方
 ↓
唯一命中
 ↓
返回
```

仅官方明确无结果 / 请求失败 / 解析失败时才走 500.com。

网络错误不能缓存为“无数据”。

批内缓存 TTL 不超过 45 秒。

## 19.5 来源章

```text
体彩官方
500
manual
```

人工修改期号或让球时清除对应来源章。

不得因为修改赛事字段而误清除期号 / 让球来源。

---

# 20. P6：Renderer、Export、History

## 20.1 唯一输入

Renderer 只接：

```text
Room domain snapshot
```

禁止从 Compose UI 拼 Markdown / JSON。

## 20.2 JSON

输出：

```text
识别结果.json
```

保持 v7 schema。

必须包含：

```text
source
coverage
session reference
revision
prematch_snapshot
archive_state
replacement_history
```

## 20.3 Markdown

继续保持 Windows 当前结构和语义：

```text
比赛基础信息
比赛结果
让球盘
胜平负
总进球
凯利
澳门时序
诊断
```

具体行序以 Golden Fixture 为准。

## 20.4 复制给 AI

每次动态读取：

```text
resources/prompts/analysis.md
```

拼接：

```text
Prompt
+
盘口数据
```

同时排除：

```text
## 比赛结果
```

现有产品明确要求赛果不能进入赛前复制文本。fileciteturn0file0L190-L196

## 20.5 原子输出

```text
旧 revision 保留
 ↓
temp output
 ↓
校验
 ↓
atomic replace
```

失败后旧有效输出仍然存在。

---

# 21. P6：Share / Copy

使用 Android Sharesheet。

文件通过：

```text
content:// URI
```

临时授权。

禁止直接暴露公共文件路径。

分享是用户主动动作。

---

# 22. P7：Compose 正式交互

UI 以工作流为中心，不做“赔率表格录入 App”。

## 22.1 页面

首版只需要：

```text
1. 首页
2. 任务详情
3. 采集状态 / LongCapture
4. 核对页
5. 结果页
6. 设置
```

## 22.2 首页

```text
当前任务
核心完成度
补充采集状态
待核对
最近任务
```

## 22.3 任务详情

```text
身份
核心 Slot
补充 Slot
采集入口
异常
结果
```

## 22.4 悬浮球

悬浮球只提供：

```text
当前屏幕
长页面
导入
当前任务
暂停 / 继续
```

参考 Jev 的“常驻副驾”模式，而不照搬其聊天业务。Jev 当前 README 明确采用悬浮入口、服务化采集、未适配 App 的手动截图 OCR，并强调服务与主 App 分离。citeturn672839search1turn672839search2

---

# 23. P7：LongCapture 用户交互

## 23.1 AUTO

显示：

```text
长页面采集 · 让球盘

第 4 段
页面正在稳定...

[暂停] [结束]
```

不显示伪精确百分比。

可显示：

```text
已采集 4 段
已发现 15 家机构
```

## 23.2 FOLLOW

显示：

```text
跟随采集

请继续向下滑动。
页面停稳后会自动采集。

已采集 3 段

[暂停]
```

用户：

```text
滑
停
滑
停
滑
停
```

系统：

```text
检测滚动
→ 检测稳定
→ 自动截图
```

## 23.3 MANUAL

```text
长页面采集

页面稳定后：
[继续采集]
```

## 23.4 异常

仅在必要时打断：

```text
⚠ 可能漏了一段
⚠ 无法确认底部
⚠ 当前页面禁止截屏
⚠ 自动滚动无进展
```

---

# 24. P7：核对 UI

核对页：

```text
原图
 ↓
bbox 高亮
 ↓
OCR 原文
 ↓
规范化值
 ↓
冲突双方
```

操作：

```text
[确认]
[修改]
[重新识别]
```

所有修改写 Room，并留下审计记录。

---

# 25. P7：状态展示原则

正常采集不打断：

```text
✓ 让球盘
```

异常才打断：

```text
⚠ 让球盘
2 项待核对
```

悬浮球只显示：

```text
○ 空闲
● 有任务
◐ OCR 中
✓ 核心齐备
⚠ 待核对
! 异常
```

---

# 26. P8：真机验收

## 26.1 长页面必须优先验收

### Case A：机构长表

```text
4～6 段
```

### Case B：澳门超长时序

```text
6～10 段或更多
```

### Case C：用户快速滑动

验证 FOLLOW 不误采半帧。

### Case D：滑 → 停 → 滑 → 停

验证 FOLLOW 核心流程。

### Case E：暂停后继续

验证覆盖关系重新建立。

### Case F：中间段缺失

强制删除一个 Segment：

```text
Coverage != COMPLETE
```

### Case G：顶部未知

必须不能 complete。

### Case H：底部未知

必须不能 complete。

### Case I：无 overlap

必须：

```text
GAP_SUSPECTED
```

### Case J：进程强杀

必须恢复采集 Session。

---

# 27. P8：设备与系统矩阵

覆盖：

```text
API 30
API 34
API 36
```

设备类型：

```text
AOSP / Pixel
Samsung
Xiaomi / HyperOS
Honor / OPPO / vivo 至少一种
```

测试：

```text
Overlay
Accessibility
Screenshot
MediaProjection
Scroll
FOLLOW
AUTO
后台
锁屏
重启
省电
进程强杀
低存储
横竖屏
分屏
FLAG_SECURE
```

Jev 当前项目实际记录过 Xiaomi / HyperOS 后台冻结和截图受限等问题，所以 OEM 验证不能被视为最后的形式验收。citeturn672839search0turn672839search1

---

# 28. P8：Capture Replay

必须记录可重放的采集日志：

```text
CaptureReplayLog
├── sessionId
├── segmentSequence
├── scrollRequest
├── observedMovement
├── stabilityResult
├── anchorCount
├── overlapResult
├── bottomEvidence
└── error
```

目的：

> 某台手机出现漏段时，可以在不重新操作真机的情况下重放 CoverageAnalyzer。

同一输入重复运行必须产生相同结果。

---

# 29. 性能指标

所有性能门槛以目标真机实测为准，不用模拟器代替。

记录：

```text
截图等待时间
Segment 保存时间
OCR p50
OCR p95
峰值内存
连续 7 Segment 吞吐
连续 10+ Segment 吞吐
温升
电量下降
磁盘占用
恢复时间
```

首版更看重：

```text
稳定
不丢图
可恢复
低热量
```

而不是追求最大并发。

---

# 30. 测试体系

## 30.1 Unit Test

覆盖：

```text
Domain
Schema
Slot
Parser
Provider
Timeline
Merge
Coverage
Classifier
Validator
```

## 30.2 Instrumentation Test

覆盖：

```text
Room
File commit
Migration
Service
Capture provider
Permission flow
```

## 30.3 Compose Test

覆盖：

```text
新建任务
切换任务
非空 Slot
替换
补充
LongCapture 暂停/继续
异常核对
分享
复制
```

## 30.4 Golden Test

对：

```text
JSON
Markdown
Parser
Merge
Coverage
```

执行严格 diff。

---

# 31. 全局硬门槛

## 31.1 Coverage

```text
缺中间段 → false-complete = 0
顶部未知 → false-complete = 0
底部未知 → false-complete = 0
相邻段无连接证据 → false-complete = 0
```

## 31.2 Evidence

所有截图派生字段：

```text
Asset ID
+
原图 bbox
```

覆盖率 100%。

网络/人工字段使用 SourceReference。

## 31.3 Conflict

冲突：

```text
两侧值
两侧 source
两侧 bbox
```

全部保留。

## 31.4 Recovery

以下任何异常：

```text
权限撤销
Service 停止
进程强杀
Room 失败
低存储
OCR 失败
```

都不能导致：

```text
完整成功
```

## 31.5 Network

网络异常不得：

```text
删除人工值
覆盖人工值
伪造来源
```

---

# 32. Release 前检查

运行：

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat connectedAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Release 不提交：

```text
build/
签名密钥
设备截图
真实任务数据
临时 OCR 数据
```

版本记录：

```text
app version
Git commit
resource version
OCR model SHA-256
APK SHA-256
device test result
known limitations
```

---

# 33. 内部 APK 交付说明

交付文档必须明确：

```text
支持 Android 11+
首版仅内部 APK
不提供自动备份 / 恢复
图片和 OCR 默认本地处理
体彩回填会联网查询赛事身份
Accessibility 采集能力受设备 / 系统 / 第三方 App 影响
受保护窗口可能无法截图
```

不得把：

```text
内部 APK
```

描述成：

```text
已满足 Google Play Accessibility 政策
```

Google Play 对非无障碍类用途的 Accessibility API 有单独披露、同意和使用限制；这不影响首版内部 APK，但必须在以后评估 Play 发布时重新走合规审查。citeturn672839search6turn672839search7

---

# 34. 最终阶段依赖图

```text
P0A 工程基线 + Schema
        │
        ├───────────────┐
        ↓               ↓
P1 设备/Capture       P0B Windows v7
        │               │
        ↓               │
P2 Room/文件/恢复       │
        │               │
        ↓               │
P3 LongCapture          │
        │               │
        ↓               │
P4 OCR Spike            │
        │               │
        ↓               │
P5 Parser/Merge         │
        │               │
        └───────┬───────┘
                ↓
P6 Renderer/Export/Network
                ↓
P7 Compose/Overlay 完整体验
                ↓
P8 真机/Release
```

---

# 35. 建议实际开发顺序

## 第一优先级：先证明“能采”

```text
P0A
→ P1
```

输出：

```text
悬浮球
当前屏幕截图
截图失败诊断
```

## 第二优先级：证明“能完整采”

```text
P2
→ P3
```

输出：

```text
LongCapture
AUTO
FOLLOW
MANUAL
Coverage
Recovery
```

## 第三优先级：证明“采到的数据可信”

```text
P4
→ P5
```

输出：

```text
OCR
Classifier
SlotRouter
Merge
Validator
Evidence
```

## 第四优先级：证明“能交付”

```text
P6
→ P7
```

输出：

```text
Markdown
JSON
复制
分享
```

## 第五优先级：证明“长期能用”

```text
P8
```

输出：

```text
真机验证
故障恢复
性能数据
release APK
```

---

# 36. 首版 Definition of Done

必须同时满足：

1. Android 11+ 真机可安装。
2. 悬浮入口稳定。
3. 当前屏幕采集可用。
4. LongCapture AUTO / FOLLOW / MANUAL 至少均有明确可验证路径。
5. 缺段、未知顶部、未知底部不会被标记为 COMPLETE。
6. 中途暂停和进程中断可以恢复。
7. 原始 Segment 不丢。
8. OCR 可重复。
9. Parser / Merge 与共同 Golden Fixture 对齐。
10. 所有截图派生字段可回溯 Asset + bbox。
11. 冲突双方均保留。
12. 四核心槽位 + 身份门禁与 Windows 语义一致。
13. Markdown / JSON 可从 Room 派生生成。
14. 赛果不会进入赛前 AI 复制文本。
15. 体彩官方优先、500.com 兜底且网络异常不破坏本地任务。
16. Release APK 可安装、可升级、可验证签名。
17. 交付说明明确 Android 版本、备份、联网和 Accessibility 限制。

---

# 37. 十条最终架构红线

```text
1. LongCapture 不以固定滑动距离作为 coverage 证据。

2. 未确认顶部、底部或中间连续性，不得 COMPLETE。

3. 不把巨大 Bitmap 作为长页面唯一数据表示。

4. 每个 Segment 必须能够独立持久化、恢复和重放。

5. Capture 与完整 OCR 解耦；原图必须先可靠落盘。

6. OCR 必须保留原始 token 和 bbox，不得先纠错后丢证据。

7. Segment Merge 不得静默覆盖冲突。

8. 自动滚动失败必须降级到 FOLLOW / MANUAL / IMPORT。

9. 所有截图派生输出都必须能够追溯到 Asset + bbox。

10. Room 是任务事实源；Markdown / JSON 永远是派生交付物。
```

---

# 38. 实施原则

本项目 Android 重构最终目标不是“手机上出现一个漂亮的 OCR 页面”，而是把 Windows 版本已经验证的业务能力，变成一个在盘口 App 旁边几乎不打断工作流的采集系统。

核心体验：

```text
用户：
看盘
↓
滑动
↓
停
↓
继续滑

程序：
采集
↓
检测稳定
↓
记录 Segment
↓
判断覆盖
↓
OCR
↓
合并
↓
核对异常
↓
生成数据
```

最终产品应呈现为：

> **用户在盘口 App 里工作；FootballScreenshotOcr 只是一直在旁边替用户把数据完整、诚实地收下来。**

现有 Windows 产品已经把这一目标建立在“准确、诚实、可复现、可沉淀”四个目标上；Android 版应以同一原则作为行为基准，而不是为了移动端便利性降低数据证据标准。fileciteturn0file0L50-L58
