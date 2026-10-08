# FootballScreenshotOcr Android 版详细开发方案

> **补充修订说明（2026-10-08）**：文末第 76 节起的契约、权限和验收规则是本方案的补充规范；与前文不一致时以补充规范为准。原始粘贴稿保留不变。

## 1. 项目目标

### 1.1 产品定位

Android 版不是 Windows 版的简单移植，也不是一个传统“图片 OCR App”。

它应被定义为：

> **盘口页面旁边的移动采集副驾。**

用户主要停留在新球体育或其他盘口 App 中，FootballScreenshotOcr 常驻后台，通过悬浮入口、当前屏幕采集、长页面连续采集、相册导入和分享入口完成数据采集。

核心原则：

```text
用户负责：
看盘口、滚动页面、产生截图

程序负责：
采集、判断页面类型、分槽、OCR、合并、校验、保存
```

---

# 2. 不能改变的产品规则

Android 重构不能改变已有业务规则。

现有项目的核心数据对象：

```text
Task
├── FixtureIdentity
├── Slots × 8
├── MatchOutcome
├── PrematchSnapshot
├── ArchiveState
└── ReplacementHistory
```

八个槽位中：

```text
1 让球盘                  核心
2 让球澳门详细变化          补充
3 胜平负                  核心
4 胜平负澳门详细变化        补充
5 总进球                  核心
6 总进球澳门详细变化        补充
7 凯利                    核心
8 必发交易盈亏              数据保留、UI 隐藏
```

四个核心槽位完成 + 比赛身份确认后自动生成文档，不需要用户点击“生成”。

以下规则全部继承：

```text
不预测
不投注建议
不自动上传
不猜测
不自动覆盖冲突
不静默截断
保留原始截图
保留证据坐标
保留 OCR 告警
```

这些是 Android 版的业务不变量，不允许为了交互方便而削弱。

---

# 3. Android 版最大的改变

Windows：

```text
复制截图
    ↓
自动落入下一个槽位
    ↓
OCR
```

Android：

```text
当前盘口页面
    ↓
悬浮入口
    ↓
单屏采集 / 长页面采集 / 导入图片
    ↓
自动判型
    ↓
自动进入 Slot
    ↓
OCR
    ↓
段落合并
    ↓
完整性检查
    ↓
正常通过 / 待核对
```

核心不是“截图”，而是：

> **Capture → Coverage → OCR → Merge → Validate**

其中 `Coverage` 和 `Merge` 必须成为一级模块。

---

# 4. 整体架构

```text
┌──────────────────────────────────────┐
│            Android UI                │
│                                      │
│ 首页 / 当前任务 / 核对 / 结果 / 设置 │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│          Overlay / Assistant         │
│                                      │
│ 悬浮球 / 快捷面板 / 当前任务进度      │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│             Capture Layer            │
│                                      │
│ Screenshot / LongCapture / Gallery   │
│ Share / Manual                        │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│           Capture Analysis           │
│                                      │
│ ImageClassifier                      │
│ ScrollController                     │
│ CoverageAnalyzer                     │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│                OCR                   │
│                                      │
│ ONNX Runtime + OCR Queue             │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│             Domain Core               │
│                                      │
│ Parser / SlotRouter / Merge          │
│ Validation / Evidence                │
└───────────────────┬──────────────────┘
                    │
                    ↓
┌──────────────────────────────────────┐
│              Storage                 │
│                                      │
│ Room / Files / DataStore             │
└───────────────────┬──────────────────┘
                    │
                    ↓
             Markdown + JSON
```

---

# 5. Android 端的三个入口

三个入口全部进入同一套 `CapturePipeline`。

## 5.1 当前屏幕采集

适用于：

```text
页面已经完整展示
页面只有一屏
```

操作：

```text
悬浮球
→ 采集当前屏幕
```

---

## 5.2 连续长页面采集

适用于：

```text
机构列表很长
澳门时序很多
表格需要多次向下滚动
```

操作：

```text
悬浮球
→ 长页面采集
```

程序负责：

```text
回顶部
→ 截图
→ 滚动
→ 等待稳定
→ 截图
→ ...
→ 判断到底
→ 完整性检查
```

---

## 5.3 相册 / 分享导入

用于：

```text
历史截图
微信截图
其他 App 图片
自动滚动失败
```

支持：

```text
系统图片选择器
Android Sharesheet
批量选择
```

---

# 6. 核心交互：悬浮球

参考 Jev，不把主 App 当作工作入口。

Jev 当前采用悬浮窗作为主工作入口，并用后台服务根据当前场景分发采集逻辑；它还提供“截屏识别一次”的手动入口。

本项目采用：

```text
●
```

点击后展开：

```text
┌──────────────────────────┐
│ 韩国U23 vs 中国U23         │
│ 核心数据 3 / 4             │
│                           │
│ 📷 当前屏幕                │
│ 📜 长页面采集              │
│ 🖼 导入截图                │
│ 📊 当前任务                │
└──────────────────────────┘
```

不展示复杂盘口表。

---

# 7. 悬浮球状态

悬浮球本身需要表现状态：

```text
○ 空闲
● 有任务
◐ OCR 中
✓ 核心数据齐备
⚠ 待核对
! 采集异常
```

这样即使用户没有打开抽屉，也可以知道当前任务状态。

---

# 8. 当前屏幕采集

首选：

```text
用户点击
 ↓
隐藏悬浮层
 ↓
截图
 ↓
恢复悬浮层
 ↓
进入 CapturePipeline
```

Android API 30 起提供 `AccessibilityService.takeScreenshot()`，并有明确的成功/失败回调。

不过这里有一个重要产品决策：

## 不让业务层依赖 AccessibilityService

抽象成：

```kotlin
interface CaptureProvider {
    suspend fun capture(): CaptureResult
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

这样未来切换平台能力，不会动上层业务。

---

# 9. 关于 AccessibilityService 的现实限制

这是 Android 方案里必须单独标红的一项。

从纯技术角度，AccessibilityService 很适合：

```text
读滚动节点
执行滚动
截图
监听界面变化
```

而 `AccessibilityNodeInfo` 提供：

```text
ACTION_SCROLL_FORWARD
ACTION_SCROLL_BACKWARD
ACTION_SCROLL_UP
ACTION_SCROLL_DOWN
ACTION_SCROLL_TO_POSITION
```

并且 Android 35 起还提供了按可见区域百分比控制滚动量的参数。

但 Google Play 对 Accessibility API 有明确限制：普通工具类 App 不能把自己声明成无障碍工具；非无障碍工具使用该 API 需要进行声明、醒目披露和用户同意，并且不能让应用借此进行不允许的自主操作。

因此：

### 内部 APK / 个人研究版

可以优先验证：

```text
Accessibility Screenshot
+
Accessibility Scroll
```

### Google Play 版本

必须从第一天保留替代实现：

```text
MediaProjection
+
手动引导滚动
+
相册导入
```

否则最后会被平台能力反过来锁死。

---

# 10. 长页面采集是一级功能

LongCapture 不是“连续截图”。

它是：

> **一次可恢复、可验证、可合并的页面采集会话。**

新增：

```text
LongCaptureSession
├── sessionId
├── taskId
├── slotId
├── sourceApp
├── captureMode
├── segments[]
├── scrollState
├── coverageState
├── completeness
└── diagnostics
```

每个 Segment：

```text
CaptureSegment
├── segmentId
├── image
├── timestamp
├── viewport
├── scrollBefore
├── scrollAfter
├── OCRResult
├── anchorSet
└── evidenceTransform
```

---

# 11. 长页面采集的三种模式

## 11.1 自动模式

```text
程序：
截
滚
等稳定
截
滚
等稳定
截
……
```

用户只需要等待。

---

## 11.2 跟随模式

针对你描述的这种情况：

```text
用户上滑
→ 停下来
→ 再上滑
→ 停下来
→ 再上滑
```

程序不强行接管滚动。

而是：

```text
监听滚动
→ 检测停止
→ 自动截屏
→ 用户继续滑
→ 再检测停止
→ 再截图
```

这是我非常推荐的第二主模式。

---

## 11.3 手动模式

自动滚动无法使用：

```text
请向下滚动
页面稳定后自动采集
```

用户只负责滑动。

程序自动：

```text
截
OCR
合并
检测遗漏
```

这相当于：

**自动采集失败 → 降级，不失败。**

---

# 12. ScrollController

定义：

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

顺序：

```text
1. Accessibility Node Scroll
2. Gesture Scroll
3. Manual Follow
```

而不是直接依赖固定的手势距离。

---

# 13. 为什么不能固定“每次上滑 700 px”

因为：

```text
手势距离
≠
内容实际位移
```

所以每次滚动后必须验证。

Android 的无障碍节点可以报告滚动状态，并支持不同方向的滚动操作；从 Android 35 起部分节点还能支持按可见区域比例控制滚动量。

程序只把：

```text
scrollForward(0.7)
```

当作请求。

真正是否移动了，要由：

```text
scroll event
+
截图变化
+
OCR anchor
```

确认。

---

# 14. Page Stability Detector

这是 LongCapture 的核心。

不能：

```text
scroll
sleep(500)
capture
```

而是：

```text
scroll
 ↓
capture A
 ↓
短暂等待
 ↓
capture B
 ↓
比较
```

如果：

```text
A ≠ B
```

说明页面还在滚动。

继续等待。

如果：

```text
A ≈ B
```

才：

```text
PAGE_STABLE
→ CaptureSegment
```

这样可以避免惯性滚动造成半帧截图和 OCR 错误。

---

# 15. 顶部检测

开始 LongCapture：

```text
PREPARE
 ↓
FIND_SCROLL_CONTAINER
 ↓
MOVE_TO_TOP
 ↓
VERIFY_TOP
```

如果不能程序化判断：

```text
请滑到顶部
[已到顶部，开始采集]
```

不能默认当前位置就是顶部。

---

# 16. 底部检测

底部检测不能只依赖一个条件。

建议综合：

```text
Accessibility scroll result
+
scroll event
+
content movement
+
OCR anchor
+
重复区域
+
页面视觉结构
```

最终得到：

```text
BOTTOM_CONFIRMED
BOTTOM_LIKELY
BOTTOM_UNKNOWN
```

只有：

```text
BOTTOM_CONFIRMED
```

才能自动完成。

---

# 17. Overlap 采集

默认：

```text
每次滚动 ≈ 65～75% 可视高度
```

保证：

```text
25～35% overlap
```

示意：

```text
S1
A
B
C
D
E
F

S2
      E
      F
      G
      H
      I
      J

S3
            I
            J
            K
            L
```

Overlap 不是浪费。

它是：

> **判断有没有漏数据的证据。**

---

# 18. CoverageAnalyzer

新增：

```text
CoverageAnalyzer
```

检查：

```text
是否从顶部开始
Segment 是否连续
Segment 是否存在足够 overlap
有没有跳跃
有没有异常大空洞
有没有重复过量
是否已经到达底部
```

输出：

```text
COMPLETE
LIKELY_COMPLETE
GAP_SUSPECTED
INCOMPLETE
```

---

# 19. 不能把“长图”做成一张巨大 Bitmap

禁止：

```text
Segment1
+
Segment2
+
Segment3
+
Segment4
→
巨大 Bitmap
```

例如一张：

```text
1440 × 30000 × 4
≈ 173 MB
```

还没算 OCR 中间数据、ONNX Tensor 和临时 Bitmap。

所以采用：

> **逻辑长图，而不是物理长图。**

---

# 20. Logical Long Page

结构：

```text
LongCapture
├── Segment S001
├── Segment S002
├── Segment S003
└── Segment S004
```

每个 OCR 值：

```json
{
  "value": "0.91",
  "segment_id": "S003",
  "bbox": [123, 217, 175, 245],
  "logical_rect": [123, 1837, 175, 1865]
}
```

于是：

```text
原始截图
    ↓
Segment
    ↓
逻辑坐标
    ↓
最终证据
```

依然能够追溯到原图。

---

# 21. Segment Merge

新增：

```text
SegmentMerger
```

负责：

```text
机构行合并
盘口行合并
澳门时序合并
统计行处理
重复行识别
冲突识别
```

例如：

```text
S1:
Bet365
Pinnacle
Betway
Macau

S2:
Betway
WilliamHill
Interwetten
```

合并：

```text
Bet365
Pinnacle
Betway
WilliamHill
Interwetten
```

而不是简单 concat。

---

# 22. 时序数据必须单独 Merge

澳门时序：

```text
S1:
10:30
11:20
12:35

S2:
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

如果：

```text
12:35
```

在两个 Segment 内容一致：

```text
DUPLICATE_EVIDENCE
```

如果：

```text
时间一样
数据不同
```

则：

```text
CONFLICT
```

必须保留双方证据，不能自动二选一。

现有项目已经明确采用“同相位不同值双向保留 + 各自证据”的规则。

---

# 23. Anchor Tracking

新增：

```text
AnchorTracker
```

从 OCR 结果中寻找稳定锚点：

```text
机构名
澳门
盘口
时间
表头
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

则：

```text
Betway
```

就是 overlap anchor。

如果：

```text
S1:
A B C D

S2:
F G H I
```

完全没有 overlap：

```text
GAP_SUSPECTED
```

系统不能继续无声采集。

---

# 24. 数据级完整性检查

页面完整性以外，再做一层：

```text
DataCompletenessAnalyzer
```

检查：

```text
预期机构数量
实际机构数量
统计行
表格结构
时序连续性
字段列完整性
```

你的现有项目已经专门处理“最大值 / 最小值 / N 家平均”等统计行，因此这些信息也可以作为完整性辅助信号。

---

# 25. 双重完整性门禁

最终：

```text
页面完整
+
数据结构完整
=
Slot 完整
```

否则：

```text
待核对
```

而不是：

```text
采了 4 张
=
完整
```

---

# 26. 长页面采集 UI

自动模式：

```text
┌─────────────────────────┐
│ 长页面采集 · 让球盘       │
├─────────────────────────┤
│                         │
│ ████████████░░░  78%    │
│                         │
│ 已采集      4 段         │
│ 已发现      15 家机构    │
│                         │
│ ● 正在等待页面稳定        │
│                         │
│ [暂停]      [结束]       │
└─────────────────────────┘
```

跟随模式：

```text
┌─────────────────────────┐
│ 跟随采集                  │
├─────────────────────────┤
│ 已采集 3 段              │
│                         │
│ 请继续向下滑动            │
│ 页面停稳后自动采集         │
│                         │
│            [暂停]         │
└─────────────────────────┘
```

---

# 27. 用户可以随时暂停

必须支持：

```text
自动滚动
→ 暂停
```

用户手动检查页面。

之后：

```text
继续
```

系统重新寻找 overlap。

这样不会出现“程序滚过头，我却无法修复”的情况。

---

# 28. 固定 Header 处理

很多盘口页面可能存在：

```text
固定 Header
固定赛事信息
固定盘口标题
固定表头
```

所以页面抽象：

```text
Page
├── FixedRegion
└── ScrollRegion
      ├── S1
      ├── S2
      └── S3
```

不能把固定 Header 每一段重复进入最终数据。

---

# 29. 自动判型

建立：

```text
ImageClassifier
```

输入：

```text
Bitmap / Segment
```

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

判定依据：

```text
关键词
版式
列结构
表头
机构名称
澳门标识
```

---

# 30. SlotRouter

```text
HANDICAP
→ Slot 1

HANDICAP_MACAU
→ Slot 2

EURO
→ Slot 3

EURO_MACAU
→ Slot 4

TOTAL_GOALS
→ Slot 5

TOTAL_GOALS_MACAU
→ Slot 6

KELLY
→ Slot 7
```

已有 Slot 时：

```text
发现 Slot 3 已存在
```

只提供：

```text
[替换]
[补充图片]
[取消]
```

不静默覆盖。

---

# 31. 任务自动绑定

第一个 Segment / 截图识别出：

```text
赛事
主队
客队
时间
```

以后：

```text
Capture
 ↓
FixtureIdentityCandidate
 ↓
已有任务匹配
```

唯一：

```text
自动绑定
```

多个：

```text
让用户选择
```

零：

```text
新建任务 / 留待人工确认
```

不使用模糊匹配。

现有项目本身也坚持主客队 + 开赛时间等条件的严格匹配，不造赛程 ID、不补造时间。

---

# 32. OCR 架构

```text
OcrService
├── OcrEngine
├── ImagePreprocessor
├── OcrQueue
├── EvidenceMapper
└── OcrDiagnostics
```

第一版：

```text
单 OCR Worker
```

不照搬 Windows：

```text
2 process × 4 threads
```

因为手机更需要：

```text
稳定
低热量
可恢复
```

而不是并行峰值。

---

# 33. OCR Queue

所有 OCR 进入：

```text
Queue<CaptureSegment>
```

同一时间只执行一个。

状态：

```text
WAITING
RUNNING
SUCCEEDED
FAILED
RETRY
```

---

# 34. 设备性能策略

根据设备动态调整：

```text
image width
OCR preprocessing
queue pacing
retry delay
```

避免：

```text
连续 7 张
→ CPU 100%
→ 手机发热
→ 降频
→ OCR 越来越慢
```

---

# 35. OCR 失败策略

失败不能让 Slot 消失。

必须保留：

```text
ImageAsset
+
CaptureSegment
+
failure diagnostics
```

显示：

```text
⚠ OCR 失败

[重新识别]
[查看图片]
```

---

# 36. 证据查看器

延续 Windows 版的证据链。

现有产品要求每个识别值可以回溯到原图坐标。

Android：

```text
点击某个值
 ↓
打开原始 Segment
 ↓
自动定位 bounding box
```

例如：

```text
Pinnacle

0.92 / 1.5 / 0.88
           ↑
        高亮这里
```

---

# 37. 核对页面

正常：

```text
✓
```

异常：

```text
⚠ 2 项待核对
```

点进去：

```text
原图
 ↓
问题位置
 ↓
OCR 数据
 ↓
[确认]
[修改]
[重新识别]
```

不让用户手动编辑整张表。

---

# 38. LongCapture 的错误分类

必须区分：

```text
CAPTURE_FAILED
SCROLL_FAILED
STABILITY_TIMEOUT
TOP_NOT_CONFIRMED
BOTTOM_NOT_CONFIRMED
OVERLAP_INSUFFICIENT
GAP_SUSPECTED
OCR_FAILED
PARSER_CONFLICT
```

这样日志才能真正用于后续设备兼容和问题定位。

---

# 39. 后台服务

建议：

```text
FootballCaptureService
```

负责：

```text
悬浮球
当前任务
采集
OCR Queue
LongCapture
状态恢复
```

服务使用前台模式保活。

但注意：Android 14+ 对前台服务类型有明确要求；如果使用 MediaProjection，需要声明对应的 `mediaProjection` 前台服务类型和权限，并且每次媒体投影会话前都需要取得用户同意。

所以 `CaptureService` 和 `MediaProjectionService` 最好逻辑分离。

---

# 40. Storage 架构

推荐：

```text
Room
+
DataStore
+
App Private Files
```

### Room

保存：

```text
Task
Slot
CaptureSession
CaptureSegment metadata
Diagnostics
```

### DataStore

保存：

```text
设置
悬浮球位置
当前任务
采集模式
OCR 参数
```

### Files

保存：

```text
原图
Markdown
JSON
history
revisions
training cases
```

---

# 41. 文件组织

```text
files/
├── staging/
│   └── jobs/
│       └── <task-id>/
│
├── output/
│   └── <task-id>/
│
├── revisions/
│   └── <task-id>/
│       └── 0001/
│
└── training_cases/
    └── v1/
```

原项目的目录语义保持不变即可，不必复制 Windows 路径。

---

# 42. Android 版不使用 Python Runtime

建议：

```text
Kotlin
+
Android SDK
+
ONNX Runtime Android
```

纯业务逻辑：

```text
纯 Kotlin/JVM
```

这样：

```text
domain
parser
validator
renderer
merge
```

都可以独立测试。

---

# 43. Module 划分

推荐：

```text
football-ocr-android/
│
├── app/
│
├── core-domain/
│
├── core-parser/
│
├── core-ocr/
│
├── core-capture/
│
├── core-storage/
│
└── test-fixtures/
```

其中：

### app

只负责：

```text
Activity
View
Overlay
Service
Android 权限
```

### core-domain

负责：

```text
Task
Slot
Identity
Evidence
```

### core-capture

负责：

```text
Capture
LongCapture
Scroll
Coverage
```

### core-parser

负责：

```text
Layout
Provider
Parser
Validator
Merge
```

---

# 44. 推荐工程目录

```text
app/
├── ui/
│   ├── MainActivity
│   ├── TaskActivity
│   ├── ReviewActivity
│   ├── ResultActivity
│   └── SettingsActivity
│
├── overlay/
│   ├── OverlayService
│   ├── OverlayBubble
│   └── CapturePanel
│
├── service/
│   ├── CaptureForegroundService
│   └── AccessibilityCaptureService
│
└── permission/
    └── PermissionCoordinator
```

---

# 45. core-capture

```text
capture/
├── CaptureProvider
├── ScreenshotCapture
├── GalleryCapture
├── ShareCapture
│
└── longcapture/
    ├── LongCaptureController
    ├── ScrollController
    ├── AccessibilityNodeScroller
    ├── GestureScroller
    ├── ManualFollowScroller
    ├── StabilityDetector
    ├── SegmentManager
    ├── AnchorTracker
    ├── CoverageAnalyzer
    ├── BottomDetector
    └── LongCaptureSession
```

---

# 46. core-ocr

```text
ocr/
├── OcrEngine
├── OcrQueue
├── BitmapPreprocessor
├── OcrResult
├── EvidenceMapper
└── OcrDiagnostics
```

---

# 47. core-parser

```text
parser/
├── ImageClassifier
├── SlotRouter
├── LayoutResolver
├── ProviderParser
├── TimelineParser
├── SegmentMerger
├── ProviderRowMerger
├── TimelineMerger
├── ConflictDetector
└── Validator
```

---

# 48. 资源迁移策略

现有：

```text
resources/providers.yml
resources/competitions.yml
resources/layouts/*.yml
resources/prompts/
resources/templates/
```

尽可能原样带入。

你现有项目已经把这些资源定义为 SSOT，而且 prompt/template 只读包内版本。

Android 第一版：

**不做图形化编辑器。**

---

# 49. Android Layout 不能直接复用 Windows 像素坐标

现有 Windows 版的截图布局需要重新校准。

采用：

```text
归一化坐标

x = pixelX / imageWidth
y = pixelY / imageHeight
```

而不是：

```text
x = 724
y = 1312
```

最终：

```text
Android Screenshot
 ↓
Normalize
 ↓
Layout
 ↓
OCR
```

这样才能兼容：

```text
1080p
1440p
不同 DPI
不同屏幕比例
```

---

# 50. 长图还需要“内容坐标系”

每张 Segment：

```text
screen coordinates
```

映射：

```text
logical page coordinates
```

例如：

```text
S1: Y=0~1000
S2: Y=700~1700
S3: Y=1400~2400
```

那么：

```text
S2 与 S1 overlap = 300
S3 与 S2 overlap = 300
```

这也是 CoverageAnalyzer 的数据基础。

---

# 51. 首页设计

只显示：

```text
当前任务
采集进度
异常
最近任务
```

示意：

```text
FootballScreenshotOcr

当前任务
韩国U23 vs 中国U23

核心数据
3 / 4

补充数据
2 / 3

⚠ 1 项待核对

[继续采集]

最近任务
...
```

---

# 52. 任务详情

```text
韩国U23 vs 中国U23

身份
✓

核心
✓ 让球
✓ 胜平负
✓ 总进球
○ 凯利

补充
✓ 让球澳门
✓ 胜平负澳门
○ 总进球澳门

[查看数据]
[继续采集]
```

---

# 53. 不建议在主界面展示完整盘口表

完整表格只在：

```text
查看数据
```

里展示。

日常采集页面只需要：

```text
是否完成
是否异常
是否可能遗漏
```

---

# 54. 采集流程 UI

默认进入：

```text
当前任务
 ↓
选择：
[单屏]
[长页面]
[相册]
```

对于重复采集，可以记住用户上次模式。

例如本场一直是澳门时序：

```text
默认：
长页面采集
```

减少重复操作。

---

# 55. 完成状态

四个核心槽位齐备：

```text
✓ 可以生成
```

自动生成：

```text
盘口数据.md
识别结果.json
```

延续现有产品机制。

---

# 56. 输出页

```text
数据已生成

✓ 盘口数据
✓ JSON
✓ 原图
✓ 证据
✓ 快照

[复制给 AI]
[只复制盘口数据]
[分享]
[查看 Markdown]
```

---

# 57. “复制给 AI”

保持 Windows 版语义：

```text
分析指令全文
+
盘口数据
```

并继续排除赛果。

现有实现已经明确这样设计，Android 版直接保持格式兼容。

---

# 58. 数据同步策略

第一版：

**不同步。**

保持：

```text
Windows
独立
Android
独立
```

唯一要求：

```text
schema 相同
资源版本可标识
```

未来再做：

```text
USB
局域网
文件同步
云同步
```

不要把同步拖进 Android MVP。

---

# 59. Windows 与 Android 的关系

正确关系不是：

```text
Windows
    ↕
Android
```

而是：

```text
            Common Data Contract
                    │
          ┌─────────┴─────────┐
          │                   │
       Windows             Android
```

共同：

```text
JSON schema
providers.yml
competitions.yml
layouts
golden fixtures
expected outputs
```

---

# 60. Python 版成为“行为基准”

不要一开始直接重写整个 Parser。

建立：

```text
Golden Fixture
```

例如：

```text
case-001/
├── screenshots/
├── expected.json
├── expected.md
└── diagnostics.json
```

Android：

```text
截图
→ Android Parser
→ output

vs

expected.json
```

自动 diff。

---

# 61. 迁移策略

严格按照：

```text
Domain
 ↓
Parser
 ↓
Merge
 ↓
Validation
 ↓
Renderer
 ↓
Storage
 ↓
UI
```

每一步：

```text
代码
+
黄金测试
```

通过后再向下。

不要：

```text
先做漂亮 UI
再补核心逻辑
```

---

# 62. LongCapture 的第一阶段测试

先不要接 OCR。

只测试：

```text
页面
 ↓
截
滚
截
滚
截
 ↓
Segment
```

验证：

```text
是否漏段
是否重复
是否覆盖
是否到底
```

---

# 63. 长图黄金测试样本

至少准备：

```text
短页面
中等页面
超长机构页
超长时序页
顶部固定 Header
底部固定操作栏
惯性滚动
滚动后停顿
连续快速滚动
手动慢速滚动
```

---

# 64. LongCapture 核心验收指标

必须测试：

```text
① 不能漏掉中间区域

② Segment overlap 足够

③ 顶部可以确认

④ 底部可以确认

⑤ 用户中途暂停可以恢复

⑥ 自动滚动失败可以降级手动

⑦ OCR 失败不会丢原图

⑧ Segment Merge 可复现

⑨ 冲突不被静默覆盖
```

---

# 65. 真机测试矩阵

至少：

```text
Pixel
Samsung
Xiaomi / HyperOS
Honor
OPPO
vivo
```

关注：

```text
悬浮窗
后台保活
Accessibility
截图
滚动
MediaProjection
省电策略
屏幕锁定
横竖屏
```

Jev 当前项目已经实际遇到 Xiaomi/HyperOS 后台冻结和截图权限问题，因此这些不能留到最终验收才测试。

---

# 66. Android 版本策略

推荐：

```text
targetSdk = 36
```

截至当前，Google Play 从 2026 年 8 月 31 日开始要求新应用和更新以 Android 16 / API 36 或更高为目标平台。

如果只是内部 APK，可以根据实际设备降低兼容目标。

如果需要支持 Android 10：

```text
minSdk = 29
```

然后：

```text
API 30+
→ Accessibility Screenshot

API 29
→ MediaProjection / 手动截图
```

这样 Android 10 不会成为产品死结。

---

# 67. MediaProjection 只作为 Capture Provider

不要直接让 LongCapture 依赖 MediaProjection。

结构：

```text
LongCaptureController
        ↓
CaptureProvider
       ↙ ↘
Accessibility   MediaProjection
```

Android 14+ 使用 MediaProjection 时需要相应前台服务类型，并且每次媒体投影会话都需要用户同意。

---

# 68. Google Play 与个人 APK 分成两条构建策略

建议：

```text
internal
```

版本：

```text
Accessibility
+
Overlay
+
LongCapture
```

以及：

```text
play
```

版本：

```text
合规 Accessibility 声明/限制
+
MediaProjection
+
Manual Follow
+
Gallery
```

不要用代码分叉业务逻辑。

只替换：

```text
CaptureProvider
```

---

# 69. 开发阶段

## P0：交互 Prototype

只实现：

```text
首页
悬浮球
当前任务
底部抽屉
模拟 Slot
```

目标：

验证用户是否真的喜欢：

> “悬浮球 → 当前任务 → 收图”

---

## P1：单屏截图

完成：

```text
悬浮球
→ 隐藏
→ 截图
→ 恢复
→ 保存
```

---

## P2：LongCapture Spike

实现：

```text
截图
→ 滚动
→ 等稳定
→ 截图
→ 重复
```

先不做 OCR。

这是整个项目第一个 Go/No-Go。

---

## P3：跟随模式

实现：

```text
用户上滑
→ 页面停稳
→ 自动截图
```

这是第二个关键能力。

---

## P4：CoverageAnalyzer

实现：

```text
overlap
anchor
gap
bottom
top
```

输出：

```text
COMPLETE
LIKELY_COMPLETE
GAP
INCOMPLETE
```

---

## P5：OCR Spike

只测试：

```text
Android OCR
vs
Windows OCR
```

不要先做完整 UI。

---

## P6：自动判型

```text
图片
→ Slot Type
```

---

## P7：Segment Merge

```text
机构合并
时序合并
冲突检测
```

---

## P8：Parser 迁移

按照 Golden Fixture 逐模块迁移。

---

## P9：证据核对

```text
bbox
→ 原图定位
```

---

## P10：完整 Task 闭环

```text
任务
→ 采集
→ LongCapture
→ OCR
→ Merge
→ Validate
→ Markdown
→ JSON
```

---

## P11：历史 / 训练样本

继续保持：

```text
history
revisions
training.jsonl
training-clean.jsonl
```

现有项目的训练样本设计本身已经具备监督学习所需结构。

---

## P12：设备兼容

最后做：

```text
厂商 ROM
后台
权限
性能
恢复
```

---

# 70. MVP 不做什么

第一版明确禁止扩张：

```text
❌ AI 预测
❌ 在线盘口
❌ 云同步
❌ 登录体系
❌ 账号系统
❌ 体彩高级玩法 UI
❌ 在线数据库
❌ 资源图形化编辑
❌ 多用户
```

核心只有：

```text
采集
OCR
结构化
验证
输出
```

---

# 71. 最终用户体验

真正理想的工作流程：

```text
新球体育
      ↓
打开某场盘口
      ↓
悬浮球常驻
      ↓
点“长页面采集”
      ↓
程序自动回顶部
      ↓
截图
      ↓
自动滚动
      ↓
等待页面稳定
      ↓
截图
      ↓
再次滚动
      ↓
……
      ↓
底部确认
      ↓
覆盖检查
      ↓
OCR
      ↓
Segment Merge
      ↓
机构/时序完整性检查
      ↓
✓ 完整
      ↓
自动进入 Slot
      ↓
四核心齐备
      ↓
自动生成 Markdown + JSON
```

如果自动滚动不可用：

```text
长页面采集

请继续向下滑动

每次页面停稳后，
系统自动采集。

[暂停]
```

用户只需要：

```text
滑
停
滑
停
滑
停
```

系统自动完成：

```text
截
识
并
验
```

---

# 72. 最终产品结构

```text
                 FootballScreenshotOcr
                          │
          ┌───────────────┴───────────────┐
          │                               │
        主 App                           副驾
          │                               │
      任务/历史                        悬浮球
      核对/结果                           │
      设置                                ↓
                                  ┌──────────────┐
                                  │ 当前屏幕采集 │
                                  │ 长页面采集   │
                                  │ 跟随采集     │
                                  │ 相册导入     │
                                  └──────┬───────┘
                                         ↓
                                  CapturePipeline
                                         ↓
                                  CoverageAnalyzer
                                         ↓
                                      OCR Queue
                                         ↓
                                  ImageClassifier
                                         ↓
                                    SlotRouter
                                         ↓
                                  SegmentMerger
                                         ↓
                                     Validator
                                         ↓
                                  ┌──────┴──────┐
                                  │             │
                                正常           异常
                                  │             │
                                  ↓             ↓
                              自动通过       人工核对
                                  └──────┬──────┘
                                         ↓
                                      Task
                                         ↓
                                  Markdown + JSON
```

# 73. 最终技术决策

最终固定以下原则：

| 项目           | 决策                                    |
| ------------ | ------------------------------------- |
| Android UI   | Kotlin + 原生 Android View，先不引入复杂 UI 框架 |
| 核心业务         | 纯 Kotlin/JVM                          |
| OCR          | ONNX Runtime Android                  |
| 常驻能力         | Foreground Service                    |
| 悬浮交互         | Overlay                               |
| 单屏采集         | CaptureProvider                       |
| 长页面采集        | LongCapture                           |
| 自动滚动         | Accessibility 优先                      |
| 滚动兜底         | Gesture                               |
| 最终兜底         | Manual Follow                         |
| 长图存储         | Segment，不生成巨大 Bitmap                  |
| 完整性          | Coverage + Data Completeness          |
| 合并           | SegmentMerger                         |
| 冲突           | 双向保留                                  |
| 证据           | Segment + bbox + logical coordinate   |
| 持久化          | Room + DataStore + Files              |
| 配置资源         | 延续 YAML SSOT                          |
| 数据格式         | 与 Windows schema 对齐                   |
| Android 最低目标 | 需要 Android 10 时 minSdk 29；否则优先 30+    |
| Play target  | API 36                                |
| 第一开发重点       | LongCapture，而不是漂亮 UI                  |

---

# 74. 最重要的三个 Go / No-Go

整个项目不要平均用力，首先只验证三个东西：

## Go/No-Go 1：悬浮副驾

```text
新球体育
→ 悬浮球
→ 点击
→ 当前屏幕截图
```

是否自然、稳定、不会遮挡。

## Go/No-Go 2：LongCapture

```text
页面
→ 自动滚
→ 停稳
→ 截
→ 再滚
→ 再截
→ 到底
```

是否能够**不漏数据**。

## Go/No-Go 3：真实盘口完整性

选择一张最复杂的真实截图：

```text
机构很多
+
澳门时序很长
+
必须多次滚动
```

最终必须能证明：

```text
页面覆盖完整
+
机构没有漏
+
时序没有漏
+
重复可以消除
+
冲突不会被覆盖
+
原图证据仍然存在
```

只有这三个都通过，才进入大规模 Android 业务迁移。

---

# 75. 最终开发原则

这个项目 Android 版最容易走偏的地方，是把重点放在：

```text
界面
动画
主题
漂亮 Slot 卡片
```

实际上最重要的是：

```text
Capture Reliability
        ↓
Scroll Reliability
        ↓
Coverage Reliability
        ↓
OCR Reliability
        ↓
Merge Reliability
        ↓
Evidence Reliability
```

因此开发优先级必须是：

> **采集可靠性 > 完整性验证 > 数据正确性 > 交互便利性 > 视觉美化。**

尤其你的项目已经明确要求“准确、诚实、可复现、可沉淀”，并且把“不确定就留空”作为核心原则，所以 Android 版也应该宁可告诉用户“疑似遗漏一段”，而不是为了让 UI 出现绿色“100%”而假装完整。

同时，这也正好解决了 Jev 当前方案暴露出的长内容盲区：Jev 的现有 OCR 兜底只处理当前可见区域，对长内容明确存在“看不全”的限制；你的项目应把它升级成一个真正的**连续页面采集引擎**。

---

# 76. 补充修订与实施契约

本节把前文架构意图落实为跨端契约、平台能力边界、状态门禁和可验证的阶段出口。这里的规则优先于前文相同主题下较笼统或相冲突的描述。

## 76.1 仓库与交付边界

当前仓库只有 Python / PySide6 Windows 应用，没有 Android Gradle 工程。建议先在同一仓库新增独立 `android/` Gradle 工程，同时新增 `contracts/` 与脱敏 `testdata/golden/`；Android 运行时不依赖 Python，Windows 现有 `src/` 也不被 Android 构建过程改写。

Android 交付物分为三类：

```text
Android APK / AAB             可安装应用
识别结果.json + 盘口数据.md    与 Windows 共用的数据交换格式
capture_sessions/<id>.json    采集过程诊断与长页面覆盖证据
```

采集会话是过程记录，不替代任务和槽位。任务仍是一个比赛；每个槽位可以有零到多个采集会话；每个会话引用一组原始图片资源。会话中止、重试或更换采集方式都不得覆盖旧图片或旧会话。

## 76.2 跨端数据契约先行

当前 Windows `TaskManifest` 的正式版本为 `schema_version = 6`，模型对未知字段采用严格拒绝策略。因此 Android 不能把长页面字段直接塞入 v6 JSON，也不能在输出仍标 v6 时写入新结构。

进入业务移植前先建立可执行契约：

```text
contracts/task-manifest.schema.json       TaskManifest v7
contracts/capture-session.schema.json     LongCapture 会话 v1
contracts/golden/                         输入、期望 JSON、期望 Markdown、诊断
```

JSON Schema 是字段类型、枚举、必填项、默认语义和兼容规则的 SSOT；Python Pydantic 与 Kotlin 序列化模型都必须通过同一批契约样本。v7 至少覆盖现有 v6 全部字段（包括 `outcome_history`、`prematch_snapshot`、`archive_state` 和 `replacement_history`），并为每个槽位记录采集来源、覆盖状态、会话引用和诊断。`ImageAsset` 继续作为原图的稳定身份，证据仍以 `asset_id` 定位原图。

迁移规则：

```text
v6 → v7：保留所有已知字段；采集来源标为 legacy；覆盖状态标为 not_recorded
v7 → v6：不承诺无损降级；不得静默丢弃覆盖状态或会话关联
Android 写出 v7：Windows 端必须先具备 v7 读取、保存和渲染能力
```

不要用“只在 Android 增加字段、Windows 暂时忽略”的方式宣称 schema 兼容。若早期 Spike 尚无端到端 v7 支持，只能把会话诊断留在独立 sidecar，且不可把该阶段产物称作完整的跨端任务交换文件。

契约测试必须覆盖：老 v6 样本升级、v7 往返序列化、枚举未知值处理、缺省与显式空值、冲突双向保留、坐标映射、赛果裁剪、`replacement_history` 保留，以及 Markdown 标题和行序。结构变化必须增加版本并提供迁移测试。

## 76.3 资源版本与当前布局事实

当前 `resources/layouts/*.yml` 使用 `[0,1]` 归一化区域，并非 Windows 固定像素坐标。因此第 49 节应按下列口径理解：坐标表示可以复用，OCR 行为和手机截图区域不能假设直接等价。

Android 应复用有版本的 `providers.yml`、`competitions.yml`、布局和 prompt/template；每份任务记录实际使用的资源版本，至少包括 provider catalog、competition catalog、layout 集合和 prompt/template 版本。需要可复现时记录内容 SHA-256。Android 新屏幕布局应使用新的 layout id/version，不应为适配手机而静默改写 Windows 已验证的布局。

移植 OCR 时必须一并对齐灰度/对比度处理、数字精修、机构名精修、补充扫描区域、统计行识别、机构排序和警告语义。不能把“加载了同一份 YAML”当作解析行为相同。

## 76.4 采集能力矩阵与 Play 边界

以下能力不能互相替代：

| 能力 | AccessibilityService | MediaProjection | 用户手动跟随 | 相册 / 分享 |
|---|---|---|---|---|
| 当前屏幕采集 | API 30+，需声明截图 capability 并获得用户启用 | 可用；需系统授权 | 由用户截屏后导入 | 可用 |
| 自动滚动第三方页面 | 可尝试节点滚动或 dispatchGesture，依赖服务授权和目标 App 暴露的节点 | 不提供滚动能力 | 不自动滚动 | 不适用 |
| 跟随滚动采集 | 可监听事件，但事件覆盖不保证完整 | 可检测图像变化，仍不能操作页面 | 用户滚动，应用检测稳定后采集 | 不适用 |
| Play 可用性 | 必须按 Accessibility API 政策申报、披露和审核；不得假设获批 | 按 MediaProjection 和前台服务规则实现 | 可作为默认兜底 | 可作为默认兜底 |

`GestureScroller` 不是独立于无障碍权限的通用方案：对其他 App 注入手势仍需要平台允许的能力。MediaProjection 只负责显示内容采集，不负责滚动。没有 Accessibility 授权时，产品必须退化为用户手动滑动、相册或分享导入，不能把自动滚动写成 Play 版保证。

API 30 起的 Accessibility 截图须在服务 metadata 声明 `canTakeScreenshot`；Android 14 起 MediaProjection 每次会话须重新取得用户同意，并声明匹配的 `mediaProjection` 前台服务类型和权限。截图服务与投影会话应分开管理生命周期，权限拒绝或会话被撤销后保留已有段并提供手动继续入口。

Play 版本不得把应用标记为无障碍工具，除非产品核心确实面向残障辅助且符合定义。Accessibility 功能须在 Play Console 声明并提供醒目披露和同意流程；在政策审核结论明确前，Play 版本的可用主路径必须不依赖 Accessibility。Internal APK 可以用于验证自动滚动，但该结果不能代表 Play 可发布性。

截至 2026-10-08，Google Play 新应用和更新要求 target Android 16 / API 36 或更高；这只决定 target，不决定 minSdk。minSdk 由实际设备矩阵决定，API 29 不提供 Accessibility 截图，需使用投影授权或手动导入。

官方依据（2026-10-08 核对）：[AccessibilityService 截图 API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback))、[MediaProjection 行为变更](https://developer.android.com/about/versions/14/behavior-changes-14#media-projection)、[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[Google Play Accessibility API 政策](https://support.google.com/googleplay/android-developer/answer/10964491)、[Google Play target API 要求](https://support.google.com/googleplay/android-developer/answer/11926878)。

## 76.5 覆盖、稳定和生成门禁

像素相似只能说明画面暂时稳定，不能说明页面没有继续滚动、没有漏段或已到底。稳定检测应屏蔽状态栏、固定 header、悬浮控件和动态广告区域，并结合滚动事件、滚动容器状态、OCR 锚点和内容位移。连续两帧相同不能单独证明顶部、底部或完整覆盖。

65%–75% 视口位移只是目标请求，不是 overlap 保证。覆盖计算必须使用实测内容位移或相邻段匹配结果。列表吸附、惯性滚动、虚拟化列表和内容刷新均可能使请求位移与实际位移不同。无法估算总页长时，不显示伪精确的百分比；显示段数、连接锚点和覆盖状态。

覆盖状态定义：

| 状态 | 含义 | 处理 |
|---|---|---|
| `not_applicable` | 单屏、相册或分享图片，没有连续页面覆盖声明 | 按单图流程校验，不声称长页面完整 |
| `not_recorded` | 旧任务或缺少会话证据 | 显示未知，不推断完整 |
| `complete` | 已确认顶部和底部，所有相邻段连续且没有未解释空洞 | 仍需通过数据完整性检查后才自动完成槽位 |
| `likely_complete` | 证据较强但顶部、底部或连接关系仍有不确定 | 进入人工核对；用户确认后输出必须保留警告 |
| `gap_suspected` / `incomplete` | 有断段、跳跃、失败或明确未到底 | 阻止自动完成；允许用户保存为明确标注的部分结果 |

长页面槽位的自动生成门禁为 `coverage=complete`、OCR 队列清空、无未解决比赛身份冲突、四个核心槽位均有效或带明确告警、并满足现有比赛身份和赛事字段要求。`likely_complete` 必须经人工核对；`gap_suspected`、`incomplete` 不得显示绿色完整状态。单图/导入槽位使用 `not_applicable`，不能因没有 LongCapture 会话而被阻断。

数据完整性检查不得把 `providers.yml slot_order` 当作每场必有机构清单。它定义机构识别与显示顺序；市场/页面实际行数会变化。应检查已识别行的列完整性、统计行契约、时序键与冲突，并把未确认机构保留为告警。`最大值 / 最小值 / N家平均` 是统计行，不按普通机构行要求初盘和即盘。

## 76.6 会话模型、证据坐标和原图策略

`CaptureSession` sidecar 至少包含：会话 id、task/slot id、采集模式、provider、状态、开始/结束时间、设备与屏幕方向、viewport/insets、段序号、原图 asset id、滚动前后观测、匹配锚点、coverage 结论和诊断。未知的滚动值应为 null，不得填估算值冒充测量值。

证据 `bbox` 的唯一基准仍是**对应原始截图上的归一化坐标** `[0,1]`，这样能直接打开 Asset 并高亮。逻辑页面坐标是可选派生信息，必须同时保存变换版本、视口偏移、裁剪边界、缩放、旋转和固定区域；坐标映射失败时保留原图 bbox，不丢证据。

每张段图都保留为独立 `ImageAsset`，不合成为超长 Bitmap。OCR 可使用派生缩放图，但必须记录变换并把 bbox 映射回原图。当前 Windows 校验边界为 15 MiB、单边 4096 像素、20 MP；Android 必须验证真实设备截图是否触界。超过边界时不能静默缩小原图或丢图；应保留原图并给出明确处理结果，跨端落盘前须先升级共享契约。

当前单槽最多 20 张图。LongCapture 必须有会话段数和总存储上限、可恢复续采、重复截图处理和用户可见的容量错误；不能依赖超过槽位限制后再失败。容量阈值应通过最复杂的机构页/时序页实测确定，并覆盖低存储空间和进程被杀场景。

## 76.7 存储事务与恢复

Room 事务不能与普通文件复制/重命名组成一个原子事务。第一版建议沿用 Windows 的可移植文件契约：任务 JSON 和原始图片为持久化 SSOT；Room 仅作可重建索引，或在性能证明需要前暂不引入 Room。DataStore 只放 UI 和轻量设置。

写入按可恢复阶段进行：先写临时图片和会话记录，校验图片可读及哈希，再原子重命名；随后写任务清单；最后标记会话已提交。启动恢复要识别临时文件、清单引用缺图、孤儿图片和未完成会话，并以诊断呈现处理结果。任何失败都保留可恢复原图，不把部分会话伪装成成功槽位。

最终输出沿用 `盘口数据.md`、`识别结果.json`、`raw/`、`history/` 和训练样本的既有语义。Android 侧的替换写入采用临时目录 + 原子切换 + 历史保留；训练案例仍是显式归档的不可变 revision。若增加数据库，必须测试数据库清空后能否从文件重建任务索引。

## 76.8 必须继承的当前业务细节

- 持久化仍有八个槽位，界面只显示七个；BETFAIR 数据槽、版式和解析器保留，虽然 UI 隐藏。第 29/30 节分类表应增加 `BETFAIR`，或明确 Android MVP 暂不自动判型但仍能导入并保留该槽数据。
- 默认截图工作流仍以 `SEQUENCE_SLOTS` 顺序自动落槽为主，当前胜平负容量为 3 张。自动判型只用于显式选择的自动路由入口；分数不唯一或不足时必须回退用户选择，不能猜槽位。
- “缺少澳门时序截图 = 横盘”只适用于已有明确约定的数据源。Android 新采集流程中无截图默认表示“未采集/未知”；只有显式人工标记或可验证迁移的源数据契约才能标为横盘。
- 四个核心槽位是让球、胜平负、总进球、凯利。补充澳门槽和 BETFAIR 不是核心门槛。身份处理至少保留赛事、主客队、开赛时间、体彩期号、主队让球、来源标记、赛事目录来源和人工确认状态。
- 当前自动生成要求核心槽位有效/带警告、比赛身份完整且有赛事、无身份冲突、没有排队中的 OCR。Android 在此基础上增加 LongCapture 覆盖门禁；不要把“用户必须额外点击确认身份”说成 Windows 现状，若产品决定新增该步骤，需明确标为 Android 新交互决策。
- 多图合并沿用现有规则：同一机构不同阶段可互补；同值重复保留全部证据来源；同一机构相位冲突、同一时序时间冲突时双方数据和证据都保留；不能依赖视觉 overlap 将解析冲突自动覆盖。
- prompt 每次复制读取打包内 `resources/prompts/analysis.md`，拼接时继续剔除 `## 比赛结果`；template 只读包内版本。资源更新必须重打包，封板留档 prompt 不得误当运行时 SSOT。

## 76.9 重排后的实施阶段与出口标准

### P0：数据契约和产品边界

决定 Android 工程位于本仓库的 `android/`；冻结 v7 JSON Schema、会话 sidecar、资源版本、七个可见槽位与八个持久槽位的映射；确定首发是 Internal、Play 还是两者。出口：Python/Kotlin 共享契约样本通过往返和迁移测试，Windows v7 读写策略已确定。

### P1：设备与权限 Spike

在 2–3 台代表设备上验证 overlay、当前屏幕采集、隐藏悬浮层、权限拒绝、手动跟随、进程被杀后的恢复。Internal 单独验证 Accessibility 截图和滚动；Play 路径验证 MediaProjection 授权和手动滚动。出口：每种目标构建的能力矩阵真实可用，没有把投影误当滚动器。

### P2：CaptureSession 与持久化恢复

先不接 OCR，完成原图落盘、段元数据、原子提交、暂停/继续、重启恢复、容量限制和清理。出口：强制结束进程发生在每个写入阶段时，原图和任务状态都能恢复或明确报错。

### P3：Coverage 合成测试与真机测试

实现 Stability、Anchor、Coverage 和 Bottom 状态，使用可控的模拟段测试重叠、重复、缺段、跳跃、固定 header、吸附滚动和动态区域。出口：所有注入缺段/未知到底样本均不得判为 `complete`；覆盖未知时不显示伪精确百分比。

### P4：Android OCR Spike

在相同脱敏截图上测 ONNX Runtime Android 与当前 RapidOCR 基线：中文机构名、负号、盘口分数、时间、数字、坐标、耗时、峰值内存和温升。出口：建立按槽位拆分的差异报告和明确的设备基线；OCR 失败仍保留原图。

### P5：Domain、Parser、Merge 和 Validator 迁移

按 `schema → parser → merge → validation` 迁移，逐个核心槽和澳门时序槽跑 golden fixture。统计行、机构顺序、别名、冲突、告警和证据坐标都参与比对。出口：没有静默丢行、静默修正或证据断链；不能达到等价的样本进入待核对，不通过猜值掩盖。

### P6：Renderer、Prompt 与 Storage 闭环

实现 v7 JSON、Markdown、历史替换、训练归档、复制 prompt 和分享导出。出口：fixture 输出与 Windows 期望结构一致；分享使用系统 `content://` 授权，不上传数据；赛果不会进入赛前复制内容。

### P7：任务 UI 与自动化

完成任务、七个可见槽、核对、结果、设置、悬浮入口；自动滚动只在已获能力授权的构建可用，手动模式始终保留。先让主要采集和核对路径可用，再做主题和视觉细节。

### P8：发布兼容和设备矩阵

覆盖 Pixel、Samsung、小米/HyperOS、荣耀、OPPO、vivo，以及 Android 版本、旋转、锁屏、省电和低存储。Play 构建完成 Accessibility 声明/审核验证、target API 检查、MediaProjection 和 foreground service 合规验证。未验证的能力在版本说明中不得宣称可用。

## 76.10 Go / No-Go 可测量标准

不得只用“感觉自然”或“页面完整”作为验收。所有长页面合成测试必须包含顶部未确认、底部未确认、连续两段无锚点、删除中间段、重复段、相同时间异值、固定 header、吸附位移、动态广告和用户中途暂停。

进入下一阶段的硬门槛：

1. 注入任意中间缺段、未确认顶部或未确认底部，Coverage 不得输出 `complete`；测试集中 false-complete 数为 0。
2. 合并时所有冲突两侧值、asset id 和原图 bbox 均保留；冲突覆盖率为 100%，相同证据可去重但来源不可丢失。
3. 每个输出字段都能追溯到原图 asset；所有 bbox 在 `[0,1]` 范围内且在对应图片上定位正确，越界映射必须产生告警。
4. 既有业务 golden fixture 的槽位、字段、排序、告警、赛果裁剪和 Markdown 标题通过率为 100%；无法复现的样本只能进入人工核对状态。
5. 权限拒绝、投影撤销、服务停止、进程强杀、存储不足和 OCR 异常均保留已采集原图，且不会生成“完整成功”结果。
6. 性能门槛先在目标机实测后冻结；至少记录每段采集等待、OCR p50/p95、单场峰值内存、温升、磁盘占用和恢复耗时，不能用桌面开发机数据替代手机验收。

---

# 77. 官方平台要求核对日期

本方案中的 Google Play 和 Android 平台要求于 **2026-10-08** 核对。正式提交应用前应重新检查上述官方链接及 Play Console 的当期声明要求；政策可能改变，方案正文中的 API 数值不构成永久承诺。
