# FootballScreenshotOcr Android 版详细开发步骤 v2.1

> 文档用途：作为 Android 工程实施顺序、阶段交付和验收的执行基线。产品业务规则沿用现有 FootballScreenshotOcr 产品定义；本文重点落实 Android 版的模块架构、Compose + Hilt 技术路线、长页面采集、OCR、跨端契约以及工程验收。
>
> 适用工程：`D:\as-workplace-tly-2026\FootballScreenshotOcr`
>
> 版本：2.1
>
> 更新日期：2026-10-08
>
> 状态：建议冻结为 Android 实施基线

---

## 1. 目标与实施边界

Android 版是本地优先的足球盘口截图采集与结构化工具。首版完成：

- 悬浮入口；
- 当前屏幕采集；
- LongCapture 长页面采集；
- AUTO / FOLLOW / MANUAL 三种长页面采集模式；
- 相册与系统分享导入；
- 页面类型自动判型；
- OCR；
- 自动槽位路由；
- 多段图片合并；
- 页面 coverage 与数据完整性检查；
- 冲突核对与原图证据查看；
- Markdown / JSON 输出；
- 体彩官方 + 500.com 的身份/期号/让球只读补全。

首版只交付个人/内部安装 APK，不安排 Google Play 上架、Play 审核或 Play 专用构建。

应用不做：

- 比赛预测；
- 投注建议；
- 云同步；
- 账号体系；
- 在线数据库；
- 云端 OCR；
- 自动上传截图或盘口 OCR 数据。

体彩期号/主队让球回填是明确例外：会向体彩官方接口及 500.com 发送必要的赛事身份查询。因此产品描述必须使用“本地优先 / 数据处理在本机”，不得表述为“完全离线”。

---

## 2. 本版相对 v2.0 的关键调整

### 2.1 Android 架构由“技术分层”调整为“中等粒度多模块 + Feature + Hilt”

首版采用多模块，但不采用过细的 Clean Architecture / api-impl / data-source-impl 层级。

固定模块：

```text
:app
:core
:data
:ocr
:parser
:feature-task
:feature-capture
:feature-review
:feature-result
:feature-settings
```

模块边界按真实职责划分，不为了“架构完整”增加额外模块。

### 2.2 Compose 作为本项目正式学习路线

UI 采用：

```text
Jetpack Compose
+ Navigation Compose
+ ViewModel
+ StateFlow
+ UDF
+ Hilt
```

页面采用 `Route -> Screen -> Components` 结构。

默认规则：

```text
Screen 不访问 Room / Network / Service
Composable 不创建业务依赖
ViewModel 管理页面状态和用户事件
Repository 负责数据访问
Hilt 负责依赖装配
```

Domain / UseCase 不预建；只有真实出现复用或 ViewModel 复杂度明显上升时才增加。

### 2.3 LongCapture 提升为首版一级核心能力

LongCapture 不再被视为“截图功能的扩展”，而是独立的数据采集能力。

必须支持：

```text
AUTO
FOLLOW
MANUAL
```

并有：

```text
Top / Bottom
Stability
Overlap
Anchor
Gap
Coverage
Replay
Circuit Breaker
```

### 2.4 Capture 与 OCR 解耦

采集先可靠保存 `CaptureSegment`，然后进入 OCR 队列。

不把长页面拼成单个巨大 Bitmap，也不让完整 OCR 阻塞每次滚动。

### 2.5 Schema v7 与 Windows 支持解耦

`P0A` 先冻结 v7 契约即可开始 Android 核心开发；Windows v7 读写/渲染作为 `P0B` 并行工作，并作为最终跨端互操作发布门槛，而不是阻塞 Android Capture Spike。

---

# 3. 架构原则

## 3.1 Source of Truth 优先级

当信息发生冲突时，使用：

```text
当前明确任务要求
    >
产品硬规则
    >
JSON Schema / Contracts
    >
Windows 当前实现与测试
    >
Golden Fixtures
    >
Android 架构文档
    >
Agent 自己推断
```

Agent 的“更优设计”不能覆盖更高优先级规则。

---

## 3.2 Android 不复制 Windows 代码，但复制 Windows 行为

Windows 是行为基准。

必须保持一致的包括：

- 槽位语义；
- 机构精确别名归一化；
- 统计行；
- 初盘/即盘；
- 澳门时序；
- 冲突处理；
- 来源标记；
- 证据语义；
- Markdown 结构；
- JSON 语义；
- prompt 组合；
- 赛果隔离。

Android 不直接依赖 Windows Python、PySide6 或 Windows 绝对路径。

---

## 3.3 “能直接调用就不要包一层”

允许：

```text
constructor injection
Repository
ViewModel
普通 Kotlin class
```

只有以下情况才增加 Interface：

- 存在多个真实实现；
- 需要隔离 Android 平台边界；
- 需要测试替身；
- 未来切换实现的概率明确存在。

典型允许：

```text
CaptureProvider
FixtureResolver
```

典型不要求：

```text
SlotRouter interface
TimelineMerger interface
TaskRepository interface + Impl
```

---

## 3.4 Hilt 使用原则

使用 Hilt，但尽量：

```text
@Inject constructor
```

优先于：

```text
@Module + @Provides
```

只有 Room、OkHttp、DataStore、外部 SDK、需要指定实现或复杂创建逻辑的对象才使用 `@Provides` / `@Binds`。

不引入额外 MVI 框架，不引入 EventBus，不自研 DI。

---

## 3.5 Compose 使用原则

固定采用：

```text
Route
  ↓
ViewModel
  ↓
UiState
  ↓
Screen
  ↓
Components
```

用户操作：

```text
UI Event
  ↓
ViewModel
  ↓
Repository / Service / Processor
  ↓
StateFlow
  ↓
Compose 重组
```

子 Composable 不自行获取 ViewModel，除非它本身就是独立导航目的地。

---

# 4. Gradle Module 结构

## 4.1 模块职责

| Module | 职责 | 依赖原则 |
|---|---|---|
| `:app` | Application、MainActivity、导航总装配、Hilt 根、Feature 组合 | 可依赖所有模块 |
| `:core` | 公共模型、枚举、错误、轻量工具、契约类型 | 不依赖上层 |
| `:data` | Room、文件、DataStore、Repository、体彩/500 网络 | 依赖 `:core` |
| `:ocr` | ONNX Runtime Android、预处理、OCR Engine、OCR Queue | 依赖 `:core` |
| `:parser` | 判型、SlotRouter、ProviderParser、TimelineMerger、Validator、Renderer | 依赖 `:core` |
| `:feature-task` | 任务列表、新建任务、任务详情 | `:data` + `:core` |
| `:feature-capture` | Capture UI、Overlay、Accessibility、LongCapture、采集状态 | `:data` + `:ocr` + `:parser` + `:core` |
| `:feature-review` | OCR/冲突/证据核对 | `:data` + `:core` |
| `:feature-result` | 结果预览、复制、分享、导出操作 | `:data` + `:parser` + `:core` |
| `:feature-settings` | 权限、轻量设置、资源/模型信息 | `:data` + `:core` |

---

## 4.2 禁止的依赖方向

禁止：

```text
:core -> Android / Compose / Room
:data -> feature-*
:parser -> :data
:parser -> Android
:ocr -> Compose
feature-* -> 其他 feature-* 的内部实现
```

Feature 之间如需共享信息，优先通过 `:data` 或 `:core` 的明确模型，而不是互相依赖。

---

## 4.3 推荐依赖图

```text
                              :app
                                │
        ┌───────────────┬───────┼───────────────┐
        ↓               ↓       ↓               ↓
 feature-task    feature-capture  feature-review  feature-result
                        │                              │
                        └────────────┐                 │
                                     ↓                 ↓
                                    :data ←────────────┘
                                     │
                      ┌──────────────┴──────────────┐
                      ↓                             ↓
                    :ocr                         :parser
                      │                             │
                      └────────────┬────────────────┘
                                   ↓
                                 :core
                                   ↓
                                  无
```

`:feature-settings` 同样只依赖 `:data` 和 `:core`。

---

# 5. 推荐工程目录

```text
FootballScreenshotOcr/
│
├── app/
├── core/
├── data/
├── ocr/
├── parser/
│
├── feature-task/
├── feature-capture/
├── feature-review/
├── feature-result/
├── feature-settings/
│
├── contracts/
├── test-fixtures/
├── docs/
└── .agents/
```

Android 业务代码：

```text
app/src/main/
core/src/main/
data/src/main/
ocr/src/main/
parser/src/main/
feature-*/src/main/
```

---

# 6. Compose 页面结构

每个 Feature 优先采用：

```text
feature-task/
└── task/
    ├── TaskListRoute.kt
    ├── TaskListScreen.kt
    ├── TaskListViewModel.kt
    ├── TaskListUiState.kt
    ├── TaskListEvent.kt
    └── components/
```

例如：

```kotlin
@Composable
fun TaskListRoute(
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TaskListScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}
```

`Screen` 只接收 State 和事件：

```kotlin
@Composable
fun TaskListScreen(
    uiState: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit
) {
    // UI only
}
```

---

# 7. 数据与状态约束

## 7.1 Room 是运行时任务事实源

Room 保存：

- Task；
- FixtureIdentity；
- Slot；
- ImageAsset 元数据；
- CaptureSession；
- CaptureSegment 元数据；
- OCR 状态与诊断；
- Conflict；
- Revision；
- 资源版本。

Markdown/JSON 是派生输出，不是第二个任务事实源。

---

## 7.2 DataStore 只保存轻量设置

允许：

- 主题；
- 悬浮球位置；
- 最近采集模式；
- 用户偏好。

禁止保存：

- Task 真相；
- OCR 结果；
- 任务完成状态；
- 自动补全来源；
- 长页面采集段。

---

# 8. 阶段总览

| 阶段 | 主要结果 | 前置 | 关键出口 |
|---|---|---|---|
| P0A | 工程基线 + v7 契约 + 模块骨架 | 无 | 契约冻结、工程可构建 |
| P0B | Windows v7 读写/渲染对接 | P0A | 共同样本通过 |
| P1 | 设备/权限/Capture Spike | P0A | 至少一条稳定实时采集路径 |
| P2 | Room/文件事务/恢复 | P0A | 断电/强杀不丢原图 |
| P3 | LongCapture L0 | P1、P2 | false-complete = 0 |
| P4 | OCR Spike + 模型冻结 | P2 | Android OCR 对照通过 |
| P5 | Parser/Merge/Validator | P0A、P3、P4 | Golden fixture 对齐 |
| P6 | Resolver + Renderer + Export | P0B、P5 | JSON/MD 跨端一致 |
| P7 | Compose + Hilt + 完整交互 | P1–P6 | 真实工作流完成 |
| P8 | 真机回归 + Internal APK | P7 | 安装/升级/恢复通过 |

P0A 完成后，P0B、P1、P2 可并行推进；P3/P4 也可以部分并行验证。

---

# 9. P0A：工程基线、架构骨架与 v7 契约

## 9.1 工程基线

1. 在 `D:\as-workplace-tly-2026\FootballScreenshotOcr` 检查 Git 状态、当前分支、未提交改动。
2. 记录当前 commit、Java、Gradle、Android SDK、NDK/构建工具状态。
3. 不覆盖现有用户改动。
4. 执行现有测试和 `assembleDebug`，形成基线。
5. 在 `libs.versions.toml` 统一管理依赖版本。
6. `minSdk=30`、`compileSdk=36`、`targetSdk=36`。
7. 保持 Java/Kotlin 工具链与现有工程一致；项目当前以 JDK 17 为开发基线时不得自行升级到更高 JDK。

## 9.2 Module 骨架

建立：

```text
:app
:core
:data
:ocr
:parser
:feature-task
:feature-capture
:feature-review
:feature-result
:feature-settings
```

每个模块先：

- 可编译；
- 有最小单元测试/边界测试；
- 无循环依赖；
- 不实现业务细节。

## 9.3 v7 Schema

从 Windows v6 整理完整字段、枚举、null 语义、默认值和输出顺序。

v7 增加：

- CaptureSession 引用；
- coverage 状态；
- segment 引用；
- 采集来源；
- LongCapture diagnostics；
- 资源/模型版本。

截图派生字段：

```text
Asset ID
+
原图归一化 bbox
```

非截图来源字段使用独立 SourceReference。

## 9.4 v6 → v7

必须定义：

```text
legacy source
not_recorded coverage
保留所有已知 v6 字段
```

禁止静默删除已知字段。

v7 → v6 不保证无损，因此禁止静默覆盖或丢弃 v7 的 coverage/session 语义。

## 9.5 Golden Fixtures

至少包括：

- 普通盘口；
- 澳门时序；
- 多机构；
- 统计行；
- 重复段；
- 同时不同值冲突；
- 未知机构；
- null/unknown；
- bbox；
- 人工修改；
- replacement history；
- outcome_history；
- prematch_snapshot；
- Markdown 行序；
- prompt 赛果裁剪。

## P0A 出口

- 干净构建通过；
- Module 依赖无环；
- Schema v7 已冻结；
- Golden fixture 可被 Python/Windows 和 Kotlin 读取；
- Agent 能根据目录结构找到 SSOT。

---

# 10. P0B：Windows v7 对接

P0B 与 P1/P2 并行，不阻塞 Android Capture Spike。

1. Windows 支持 v7 读取/保存。
2. Windows renderer 支持 v7 新字段。
3. v6 → v7 迁移测试通过。
4. 对共同 fixture 做 JSON diff。
5. 对 Markdown 做结构 diff。
6. 明确 Android 导出在什么时候可以声明“Windows 兼容”。

## P0B 出口

至少：

```text
共同 JSON 字段语义一致
共同 Markdown 结构一致
迁移无已知字段丢失
```

未达到之前，Android 本地写 v7 可以继续，但不能宣称跨端互操作已完成。

---

# 11. P1：设备、权限与 Capture Spike

这是第一个真正的 Go / No-Go 阶段。

## 11.1 目标

先证明：

```text
新球体育
 ↓
悬浮球
 ↓
采集当前屏幕
 ↓
得到可靠 Bitmap
```

暂不要求 OCR。

## 11.2 Accessibility 能力

验证：

- screenshot 能力；
- window content 能力；
- gesture 能力；
- 用户授权/撤销；
- overlay 授权/撤销；
- 服务停止后的恢复。

## 11.3 MediaProjection

作为独立截图 Provider。

职责只限于：

```text
提供屏幕帧
```

不负责自动滚动。

Android 14+ 相关授权和会话限制必须真实测试。

## 11.4 降级链

```text
Accessibility screenshot
        ↓失败
MediaProjection screenshot
        ↓不可用
Manual follow / manual screenshot
        ↓
Gallery / Share import
```

业务层只能依赖 `CaptureProvider`，不能依赖具体 Android API。

## 11.5 FLAG_SECURE

必须测试目标 App 页面禁止截图的情况，并产生：

```text
CAPTURE_BLOCKED_BY_WINDOW_SECURITY
```

不得显示伪成功。

## 11.6 悬浮层

验证：

```text
显示
→ 点击
→ 隐藏
→ 截图
→ 恢复
```

悬浮球不能出现在最终截图中。

## P1 出口

至少三类真实设备、API 30/34/36 覆盖。

至少一条实时采集路径和一条图片导入路径可稳定使用；没有验证通过的能力不得进入产品 UI 为“已支持”。

---

# 12. P2：Room、文件事务与恢复

## 12.1 Room

实体：

```text
TaskEntity
FixtureEntity
SlotEntity
AssetEntity
CaptureSessionEntity
CaptureSegmentEntity
DiagnosticEntity
RevisionEntity
```

具体实体数量可以合并，但数据库必须保留明确主键、关系和状态语义。

## 12.2 Migration

开启 Room schema export。

每次 schema 改动：

```text
version N
→ migration
→ migration test
```

禁止：

```text
fallbackToDestructiveMigration
```

## 12.3 文件提交

固定协议：

```text
Room pending
→ temp file
→ decode/size/SHA256 校验
→ atomic rename
→ Room committed
```

崩溃后：

```text
reconciliation
```

处理：

- 可恢复临时文件；
- 缺失引用；
- 孤儿文件；
- 数据库提交失败。

不得静默删除无法解释的原图。

## 12.4 原图原则

原图：

- 不进 Room BLOB；
- 保存在应用私有目录；
- 文件名不暴露队名/赛事等隐私；
- SHA-256 去重。

## P2 出口

强杀、低存储、IO 失败、DB transaction failure 后：

```text
已有原图不丢
任务不伪完整
可以继续恢复
```

---

# 13. P3：LongCapture L0

LongCapture 是 Android 版最重要的系统能力之一。

## 13.1 LongCapture 与 OCR 分离

L0 只负责：

```text
Capture
Scroll
Stability
Segment
Coverage
Recovery
```

L1 才负责：

```text
OCR
Anchor
Parser
Merge
Data completeness
```

## 13.2 三种模式

```text
AUTO
FOLLOW
MANUAL
```

### AUTO

```text
回顶部
→ capture
→ scroll
→ wait stable
→ capture
→ ...
→ bottom check
```

### FOLLOW

用户负责滚动：

```text
上滑
→ 页面稳定
→ 自动 capture
→ 用户继续上滑
→ 页面稳定
→ 自动 capture
```

若 Accessibility scroll event 不可用，可使用屏幕变化 + 稳定检测作为降级。

### MANUAL

用户明确点击“继续采集”后保存当前 Segment。

## 13.3 状态机

推荐：

```text
CREATED
 ↓
PREPARING
 ↓
MOVING_TO_TOP
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
CHECKING_BOTTOM
 ↓
COMPLETE
```

特殊状态：

```text
PAUSED
INTERRUPTED
REVIEW_REQUIRED
FAILED
```

异常原因：

```text
TOP_UNKNOWN
BOTTOM_UNKNOWN
SCROLL_BLOCKED
STABILITY_TIMEOUT
GAP_SUSPECTED
CAPTURE_BLOCKED
```

## 13.4 稳定检测

不能使用单一固定 sleep。

至少综合：

- 屏幕内容变化；
- scroll event；
- OCR/anchor 变化（可选轻量）；
- 固定区域排除；
- 目标滚动区域稳定。

连续两帧相同只能作为一个信号，不得单独证明稳定。

## 13.5 overlap

默认每次实际滚动约覆盖可见内容的 65%～75%，保留约 25%～35% overlap。

滚动请求值不是 coverage 证据。

必须记录：

```text
requested_scroll
observed_scroll_delta
```

## 13.6 Top / Bottom

顶部：

- 自动回顶并验证；
- 无法验证时提示用户。

底部：

综合：

- scroll result；
- 内容位移；
- overlap；
- anchor；
- 重复区域；
- 页面底部证据。

不得因为“滚动失败”就默认到底。

## 13.7 Coverage Evidence

每个 session 保存：

```text
TopConfirmed
BottomConfirmed
SegmentCount
MinOverlap
GapCount
GapRegions
DuplicateRegions
ScrollProgress
EndEvidence
```

coverage 状态：

```text
NOT_APPLICABLE
NOT_RECORDED
COMPLETE
LIKELY_COMPLETE
GAP_SUSPECTED
INCOMPLETE
```

只有：

```text
TopConfirmed
AND BottomConfirmed
AND 相邻 Segment 全连接
AND GapCount == 0
```

才能 `COMPLETE`。

## 13.8 不使用巨大长图 Bitmap

存储：

```text
S001
S002
S003
...
```

不要：

```text
1440 × 30000 巨大 Bitmap
```

需要长页面浏览时，再以逻辑坐标组合显示。

## 13.9 Anchor / Gap

Segment 之间通过稳定文本/结构寻找 overlap anchor。

完全没有连接证据：

```text
GAP_SUSPECTED
```

不能静默接受。

## 13.10 Circuit Breaker

LongCapture 必须有：

```text
maxSegments
maxScrollAttempts
maxNoProgress
maxStabilityWait
maxSessionDuration
```

例如连续多次 scroll 无新增内容或无有效位移时，自动停止并转 `REVIEW_REQUIRED`。

具体阈值由真机数据确定，不在第一版拍脑袋固定成业务规则。

## 13.11 Replay Log

每个 LongCapture 保存：

```text
Segment sequence
scroll request
observed delta
stability result
anchor count
coverage decision
```

用于问题复现和 CoverageAnalyzer 离线回放。

## 13.12 固定 Header / 动态区域

页面分成：

```text
FixedRegion
ScrollRegion
```

只对确认固定的区域进行 mask。

不允许把未知动态区域直接排除出完整性判断。

## P3 出口

硬门槛：

```text
任意注入缺段
→ false-complete = 0
```

此外：

- 顶部未知不能 COMPLETE；
- 底部未知不能 COMPLETE；
- overlap 不足不能 COMPLETE；
- 中间存在疑似 gap 不能 COMPLETE；
- 暂停/继续可恢复；
- 进程中断后 Segment 不丢。

---

# 14. P4：OCR Spike 与模型冻结

## 14.1 独立 `:ocr` 模块

职责：

```text
OcrEngine
BitmapPreprocessor
OcrQueue
EvidenceMapper
OcrDiagnostics
```

UI 不依赖 ONNX Runtime API。

## 14.2 模型

记录：

- 模型名称；
- 版本；
- 来源；
- License；
- 文件大小；
- SHA-256；
- 预处理版本。

运行时禁止无提示下载模型。

模型不合格时停止阶段，不用未验证模型“救通过率”。

## 14.3 CPU 基线

CPU 作为首要兼容基线。

不把 NNAPI 作为新架构依赖。

如以后验证 GPU/厂商加速，也不得改变业务输出语义。

## 14.4 OCR Queue

默认：

```text
单 OCR Worker
+ 有界队列
+ 可取消
+ 可重试
+ 持久状态
```

Capture 不应因为 OCR 需要完整处理而卡住整个 LongCapture。

## 14.5 Evidence

OCR 结果必须保留：

```text
raw text/token
confidence
bbox
asset id
model version
preprocess version
```

不能在“纠错”后丢弃原始 token。

## 14.6 Windows 对照

使用相同脱敏 fixture：

```text
Windows OCR
vs
Android OCR
```

对比：

- 中文机构名；
- 数字；
- 负号；
- 盘口；
- 时间；
- 统计行；
- bbox；
- 耗时；
- 内存；
- 连续处理稳定性。

## P4 出口

模型冻结，目标设备达到预先定义的真实指标；关键字段达不到门槛时进入人工核对，不降低门槛换“绿色成功”。

---

# 15. P5：Parser、Merge、Validator

`:parser` 保持纯 Kotlin，不依赖 Android、Room、Network、Compose。

## 15.1 迁移顺序

```text
model adaptation
→ image classification
→ SlotRouter
→ ProviderParser
→ TimelineParser
→ ProviderRowMerger
→ TimelineMerger
→ Validator
```

## 15.2 Slot

八槽位保持：

```text
1 让球
2 让球澳门
3 胜平负
4 胜平负澳门
5 总进球
6 总进球澳门
7 凯利
8 BETFAIR
```

第 8 槽 UI 继续隐藏，但数据层保留。

## 15.3 判型

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

分数不足或不唯一：

```text
UNKNOWN
→ 人工选择
```

不能模糊猜测。

## 15.4 机构规则

保持精确别名归一化。

词表外：

```text
原文保留
+
WARNING
```

统计行：

```text
最大值
最小值
N 家平均
```

不按机构处理。

## 15.5 Segment Merge

同值重复：

```text
可去重
但保留所有 source asset
```

同语义位置不同值：

```text
CONFLICT
保留双方
保留双方 bbox
```

## 15.6 Timeline Merge

时间相同且值相同：

```text
DUPLICATE_EVIDENCE
```

时间相同值不同：

```text
CONFLICT
```

禁止自动选择“最新/更可信”的一个覆盖另一个。

## 15.7 完整性

区分：

```text
页面完整性
```

与：

```text
数据完整性
```

例如页面已经到达底部，但预期 16 家机构只识别到 11 家：

```text
Page = COMPLETE
Data = INCOMPLETE
```

最终 Slot 仍然不能标记为业务意义上的完整成功。

## P5 出口

Golden fixture：

```text
slot
value
row order
warning
conflict
source
bbox
```

通过率 100%。无法解释的差异进入 REVIEW_REQUIRED，不修改 expected 输出来制造通过。

---

# 16. P5B：体彩官方 + 500.com Resolver

网络层位于 `:data`。

## 16.1 接口

建议：

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

## 16.2 官方源

主源：体彩官方 webapi。

规则：

- 必要浏览器 User-Agent；
- 唯一精确归一化匹配；
- 批内缓存 TTL 不超过 45 秒；
- 网络异常不缓存为“无数据”；
- 已开赛下架等异常不猜。

## 16.3 500.com

仅在官方无唯一结果或失败时兜底。

保留 Windows 当前单向归一化规则，不增加会产生错误前缀的别名。

## 16.4 网络边界

只发送：

```text
比赛身份查询所需字段
```

不得发送：

- 截图；
- OCR 表格；
- Task ID；
- prompt；
- 盘口明细。

UI 必须明确“此功能会联网查询身份数据”。

## P5B 出口

网络失败不会：

```text
覆盖人工值
制造伪来源
导致任务失败
```

---

# 17. P6：Renderer、Export、History

## 17.1 唯一输入

Renderer 输入：

```text
Room domain snapshot
```

不从 Compose 状态直接拼 Markdown。

## 17.2 JSON

生成：

```text
识别结果.json
```

保留：

- replacement_history；
- outcome_history；
- prematch_snapshot；
- archive_state；
- source references；
- coverage/session diagnostics。

未知状态保持 UNKNOWN / NULL，不生成虚假默认值。

## 17.3 Markdown

保持 Windows v7 renderer 语义：

- 标题；
- 槽位顺序；
- 机构顺序；
- 澳门时序；
- 警告；
- 来源。

## 17.4 Prompt

每次复制实时读取包内：

```text
resources/prompts/analysis.md
```

继续排除：

```text
## 比赛结果
```

保持模板只读包内资源。

## 17.5 历史

替换输出：

```text
旧 revision 保留
→ temp write
→ validation
→ atomic switch
```

失败后旧版本仍然有效。

## 17.6 Share

使用 Android Sharesheet + `content://` URI。

不得通过公开路径暴露 App 私有文件。

## P6 出口

Android 与 Windows v7：

```text
JSON 结构一致
Markdown 结构一致
prompt 赛果裁剪一致
```

跨端兼容测试通过后，才标记 Android 导出“Windows v7 compatible”。

---

# 18. P7：Compose + Hilt 完整用户流程

本阶段开始正式建设可用 UI。

## 18.1 Navigation

主导航：

```text
TaskList
TaskDetail
Capture
Review
Result
Settings
```

使用 Navigation Compose。

## 18.2 Task Feature

页面：

```text
TaskListScreen
TaskCreateScreen
TaskDetailScreen
```

状态：

```text
loading
ready
empty
error
```

列表显示：

- 处理中；
- 待核对；
- 部分采集；
- 冲突；
- 可生成。

## 18.3 Capture Feature

`feature-capture` 负责：

- CaptureScreen；
- CaptureViewModel；
- OverlayService；
- Accessibility Service 装配；
- LongCapture；
- Capture 状态展示；
- 当前任务；
- 采集模式。

UI：

```text
单屏采集
长页面采集
相册导入
分享导入
```

## 18.4 长页面 UI

自动模式：

```text
当前 Slot
采集段数
稳定等待
暂停 / 继续
顶部 / 底部状态
Coverage 警告
```

Follow 模式：

```text
请继续向下滑动
页面稳定后自动采集
```

不要显示伪精确百分比。

## 18.5 Review Feature

提供：

- 原图；
- segment；
- bbox 定位；
- OCR 原文；
- 冲突双方；
- 来源；
- 修正。

人工修改必须记录审计。

## 18.6 Result Feature

展示：

```text
可以生成
需核对
不完整
```

操作：

```text
复制给 AI
只复制数据
分享
查看 Markdown
```

## 18.7 Settings Feature

显示：

- Accessibility 状态；
- Overlay 状态；
- MediaProjection 状态；
- 采集默认模式；
- 悬浮球；
- 数据目录/存储占用（如实现）；
- OCR 模型版本；
- 资源版本；
- 无备份提示；
- 网络补全说明。

## 18.8 Hilt

根：

```kotlin
@HiltAndroidApp
class FootballOcrApplication : Application()
```

页面：

```kotlin
@HiltViewModel
class TaskViewModel @Inject constructor(...)
```

优先构造器注入。

不创建：

```text
ServiceLocator
Application.getInstance().getXxx()
```

## 18.9 UI 测试

至少覆盖：

- 新建任务；
- 非空 Slot 替换；
- 长页面暂停/恢复；
- 权限拒绝；
- 待核对；
- 冲突查看；
- 结果分享；
- 空列表；
- 旋转/重建。

---

# 19. P8：真机回归与 Internal APK

## 19.1 设备矩阵

至少覆盖：

```text
Pixel / AOSP
Samsung / One UI
Xiaomi / HyperOS
```

API：

```text
30
34
36
```

## 19.2 必测场景

### Capture

- 悬浮球；
- 当前屏幕；
- Accessibility；
- MediaProjection；
- 手动跟随；
- 相册；
- Share。

### LongCapture

- 机构长页；
- 澳门时序长页；
- 自动快速滚动；
- 用户慢速滚动；
- 滑/停/滑/停；
- 自动暂停；
- 手动调整后继续；
- 中间缺段；
- 底部不明确；
- 固定 Header；
- 动态广告。

### Recovery

- 进程强杀；
- 服务停止；
- 屏幕锁定；
- 省电模式；
- 重启设备；
- 低存储；
- DB migration；
- 升级安装。

### Network

- 无网；
- 官方成功；
- 官方 567；
- 官方超时；
- 500 兜底；
- 双源失败；
- 人工值保留。

## 19.3 Build

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat connectedAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

## 19.4 Release 记录

保存：

```text
version
Git commit
schema version
resource version
OCR model SHA-256
APK SHA-256
设备测试结果
已知限制
```

签名密钥禁止进入 Git 和 APK 输出目录。

---

# 20. 全局质量门槛

以下为硬门槛。

## 20.1 Capture

```text
缺段
未确认顶部
未确认底部
连接失败
```

均不得 COMPLETE。

## 20.2 Evidence

所有截图派生字段必须：

```text
Asset ID
+
原图 bbox
```

所有非截图来源字段必须使用独立来源引用。

## 20.3 Conflict

冲突双方：

```text
100% 保留
100% 有来源
100% 有 bbox（若来自截图）
```

## 20.4 Storage

原图不得因：

```text
进程崩溃
低存储
Room 失败
```

而静默丢失。

## 20.5 Network

网络错误不得：

```text
伪造数据
覆盖人工值
写入伪来源
```

## 20.6 Cross-platform

Golden fixture：

```text
JSON
Markdown
Prompt
```

关键语义必须一致。

---

# 21. Agent / Codex 执行规范

## 21.1 开发前

复杂任务先：

```text
读 AGENTS.md
→ 查相关 Skill
→ 查当前实现
→ 查 Golden Fixture
→ 建 ExecPlan
```

小改动可直接实施。

## 21.2 不允许的行为

Agent 不得：

- 为绿色测试修改 expected；
- 降低完整性门槛；
- 删除失败数据；
- 用 destructive migration；
- 为规避架构问题增加无必要的抽象层；
- 将业务逻辑放入 Composable；
- 为单一实现建立 `Interface + Impl`；
- 直接从开发机绝对路径读取资源；
- 把网络失败解释成“无数据”；
- 把 Segment 采集成功解释为页面完整。

## 21.3 每次改动必须回答

```text
修改了什么？
当前行为在哪里定义？
SSOT 是什么？
哪些行为不能改变？
用什么测试证明没有破坏？
```

## 21.4 最小改动原则

禁止 opportunistic refactor。

任务只要求：

```text
增加 FOLLOW 模式
```

不得顺手：

```text
重构 Repository
升级 Compose
重写 Room
更换 DI
重命名整个 capture 包
```

除非这些改动是当前任务真实必要条件。

---

# 22. Definition of Done

一个阶段只有同时满足：

```text
代码完成
+
相关测试通过
+
真实失败路径验证
+
没有改变无关行为
+
诊断信息真实
+
输出经过验证
+
必要时完成 migration/recovery
```

才可以标记完成。

“编译通过”不是完成条件。

“UI 看起来完整”不是完成条件。

“一次真实设备成功演示”不是完成条件。

---

# 23. 首版最终工作流验收

完整验收必须能够完成：

```text
打开新球体育
        ↓
FootballScreenshotOcr 悬浮球常驻
        ↓
创建/选择当前比赛
        ↓
进入长页面采集
        ↓
回到顶部并确认
        ↓
Capture Segment 1
        ↓
滚动
        ↓
等待稳定
        ↓
Capture Segment 2
        ↓
……
        ↓
用户可以随时暂停
        ↓
继续采集
        ↓
到达底部并确认
        ↓
Coverage 完整性检查
        ↓
OCR 队列处理全部 Segment
        ↓
自动判型
        ↓
自动 SlotRouter
        ↓
Segment Merge
        ↓
机构/时序数据完整性检查
        ↓
需要时进入 Review
        ↓
四个核心槽位满足门禁
        ↓
自动生成 JSON + Markdown
        ↓
复制给 AI / Share
```

全流程要求：

```text
用户不需要手动选择每张截图属于哪个 Slot
用户不需要手工拼长图
用户不需要录入 OCR 数值
程序不猜测不确定数据
程序不静默丢失 Segment
程序不静默覆盖冲突
```

---

# 24. 首版明确不做

```text
❌ AI 预测
❌ 投注建议
❌ 云同步
❌ 登录/账号
❌ 云端 OCR
❌ 在线盘口
❌ 在线数据库
❌ 资源图形化编辑
❌ 体彩高级玩法 UI
❌ 多用户
❌ 自动上传截图
```

---

# 25. 开发顺序最终冻结

```text
P0A  工程基线 + Compose/Hilt 多模块骨架 + v7 Contract
 ↓
P0B  Windows v7 对接（可并行）
 ↓
P1   Capture / Overlay / Accessibility / Projection Spike
 ↓
P2   Room + File + Recovery
 ↓
P3   LongCapture AUTO / FOLLOW / MANUAL + Coverage
 ↓
P4   ONNX OCR + 模型冻结
 ↓
P5   Parser + Merge + Validator
 ↓
P5B  体彩/500 Resolver
 ↓
P6   Renderer + Export + History
 ↓
P7   Compose UI + Hilt + Navigation + 完整工作流
 ↓
P8   真机回归 + Internal APK
```

第一优先级不是视觉完成度，而是：

```text
采集可靠性
>
LongCapture 完整性
>
OCR 正确性
>
Merge 正确性
>
证据链
>
恢复能力
>
UI 完整度
```

最终目标是让 Android 版成为：

> **新球体育等盘口 App 旁边的本地“盘口采集副驾”，而不是另一个需要用户主动维护的 OCR 表格录入工具。**
