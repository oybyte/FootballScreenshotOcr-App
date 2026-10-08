# FootballScreenshotOcr

FootballScreenshotOcr 是一个面向足球盘口截图的本地化采集与结构化工具。Android 版的目标不是做一个普通的图片 OCR 应用，而是成为盘口页面旁边的“移动采集副驾”：用户继续在新球体育或其他盘口应用中查看页面，FootballScreenshotOcr 通过悬浮入口、当前屏幕采集、长页面采集、相册导入和系统分享入口获取截图，然后完成 OCR、数据合并、完整性检查和结果导出。

本项目强调准确、诚实、可复现和可追溯。应用不预测比赛、不提供投注建议、不自动上传数据、不猜测缺失值，也不静默覆盖冲突数据；每个识别结果都应能回溯到原始截图及其坐标。

## 当前状态

仓库目前是 Android 初始工程，已完成 Gradle 基础配置和示例页面，核心业务能力尚未实现。方案文档描述的是目标产品和分阶段实施契约，不代表当前 APK 已经支持对应功能。

当前可确认的工程事实：

- 应用模块为 `app`，包名为 `com.fifa.ocr`。
- 当前入口使用 Jetpack Compose 示例页面，显示 `Hello Android!`。
- `compileSdk`、`targetSdk` 为 API 36，当前模板 `minSdk` 为 24。
- 当前仓库尚未包含 `core-domain`、`core-capture`、`core-ocr`、`core-parser` 等计划中的业务模块。
- 方案第 76 节仍按“仓库只有 Python / PySide6 Windows 应用”的历史前提描述交付边界；当前检出实际是 Android Gradle 工程，后续不应机械地再创建一个重复的 `android/` 根工程。
- 方案引用的 `resources/`、`contracts/`、`testdata/golden/` 和 Windows 端实现当前不在本仓库中；跨端迁移前需要确认它们的来源、版本和纳入方式。
- Android 详细方案见 [FootballScreenshotOcr-Android版详细开发方案-补充完善版.md](FootballScreenshotOcr-Android版详细开发方案-补充完善版.md)。

方案最终建议使用原生 Android View 作为 UI，而当前模板使用 Compose；正式进入 UI 实施前需要明确是迁移到原生 View，还是记录为有意偏离并统一后续技术决策。

## 产品用途

典型工作流如下：

```text
盘口 App
  -> 悬浮球
  -> 当前屏幕 / 长页面 / 相册或分享导入
  -> CapturePipeline
  -> 页面覆盖与稳定性检查
  -> OCR Queue
  -> 页面类型识别与槽位路由
  -> 多段合并与冲突检测
  -> 数据完整性和证据校验
  -> 任务状态更新
  -> Markdown + JSON
```

用户负责查看页面和在必要时滚动；程序负责采集、判型、分槽、OCR、合并、校验、保存和展示异常。自动采集不可用时，应降级到用户手动滚动、相册导入或分享导入，而不是把一次失败的自动滚动伪装成完整结果。

## 业务对象与槽位

任务以一场比赛为单位，核心对象为：

```text
Task
├── FixtureIdentity
├── Slots
├── MatchOutcome
├── PrematchSnapshot
├── ArchiveState
└── ReplacementHistory
```

持久化保留八个槽位，界面可只展示七个：

| 槽位 | 内容 | 业务角色 |
| --- | --- | --- |
| 1 | 让球盘 | 核心 |
| 2 | 让球澳门详细变化 | 补充 |
| 3 | 胜平负 | 核心 |
| 4 | 胜平负澳门详细变化 | 补充 |
| 5 | 总进球 | 核心 |
| 6 | 总进球澳门详细变化 | 补充 |
| 7 | 凯利 | 核心 |
| 8 | 必发交易盈亏 / BETFAIR 数据 | 保留数据，默认隐藏 |

四个核心槽位有效或带明确告警、比赛身份和赛事信息完整、无未解决冲突、OCR 队列清空，并且长页面通过覆盖门禁后，才允许自动生成结果。补充槽位和历史数据不能被核心门禁误删或静默忽略。

## 采集入口

所有入口最终进入同一套 `CapturePipeline`：

1. **当前屏幕采集**：页面已完整显示时，隐藏悬浮层、截取当前屏幕、恢复悬浮层并保存原图。
2. **连续长页面采集**：回到顶部后分段截图、滚动、等待页面稳定、继续采集并确认到底。
3. **相册 / 分享导入**：处理历史截图、微信截图、其他应用图片或自动滚动不可用的场景，支持系统图片选择器和 Android Sharesheet。

长页面采集有三种模式：

- 自动模式：应用控制滚动并在页面稳定后采集。
- 跟随模式：用户滚动，应用检测停稳后自动采集。
- 手动模式：应用只提示用户继续滚动和停稳，仍负责截图、OCR、合并和检查。

自动模式不是唯一成功路径。Accessibility、MediaProjection、设备厂商限制或权限拒绝都可能要求切换到手动模式或导入模式。

## 长页面可靠性

LongCapture 是一段可暂停、可恢复、可诊断的采集会话，不是简单地连续拼图。每个段至少需要保留原图、时间、视口、滚动前后观测、OCR 结果、锚点和证据坐标。

长页面不合成为一张巨大 Bitmap，而是保留独立的 `CaptureSegment`，通过逻辑页面坐标描述段之间的关系。`CoverageAnalyzer` 和 `DataCompletenessAnalyzer` 共同决定结果是否完整：

| 覆盖状态 | 含义 | 处理 |
| --- | --- | --- |
| `not_applicable` | 单图、相册或分享导入，没有连续页面覆盖声明 | 按单图流程校验 |
| `not_recorded` | 缺少会话证据或来自旧任务 | 显示未知，不推断完整 |
| `complete` | 顶部、底部、相邻段连接和空洞都已确认 | 还需通过数据完整性检查 |
| `likely_complete` | 证据较强但仍有不确定点 | 人工核对后输出并保留告警 |
| `gap_suspected` / `incomplete` | 断段、跳跃、失败或未确认到底 | 阻止自动完成，允许保存部分结果 |

固定 Header、吸附滚动、惯性滚动、动态广告和虚拟化列表都可能使请求滚动距离与实际内容位移不一致。因此“滚动了几次”或“采集了几张”不能作为完整性的依据；必须结合稳定性、锚点、重叠、滚动事件和底部确认。

## OCR、合并与证据

Android 侧计划使用 Kotlin、ONNX Runtime Android 和单 OCR Worker：

```text
OcrService
├── OcrEngine
├── ImagePreprocessor
├── OcrQueue
├── EvidenceMapper
└── OcrDiagnostics
```

OCR 失败时仍保留原始 `ImageAsset`、采集段和失败诊断，并提供重新识别、查看原图和人工核对入口。解析流程应先识别页面类型，再路由到槽位；已有槽位只能由用户明确选择替换、补充或取消，不能静默覆盖。

多段合并需要区分机构行和澳门时序：同一段内容重复可以去重，但同一机构或同一时间出现不同值时，双方数据、来源和证据都必须保留并标记冲突。最终每个字段都应能定位到对应原图的归一化 `bbox`；逻辑页面坐标只能作为派生信息，不能替代原图证据。

## 输出与存储

目标输出沿用现有 Windows 版本语义：

```text
盘口数据.md
识别结果.json
capture_sessions/<id>.json
raw/ 原始截图
history/ 历史版本
training_cases/ 训练样本
```

Android 采集会话是过程记录，不替代比赛任务和槽位。任务、图片、会话和历史版本应支持暂停、恢复、重启恢复以及进程被杀后的诊断。推荐以任务 JSON 和原始图片作为可移植持久化事实来源，数据库只承担可重建索引或经验证后的查询加速；任何文件提交失败都必须保留可恢复的原图，不能生成“完整成功”结果。

“复制给 AI”继续使用“分析指令全文 + 盘口数据”的格式，并排除赛果内容。应用只在本地处理数据，不自动上传。

## 目标架构

计划中的模块边界如下：

```text
app/             Activity、UI、悬浮层、Service、权限
core-domain/     Task、Slot、比赛身份、证据模型
core-capture/    截图、滚动、LongCapture、Coverage
core-ocr/        OCR、队列、预处理、证据映射
core-parser/     判型、槽位路由、解析、合并、校验
core-storage/    文件、数据库、设置和恢复
test-fixtures/   脱敏截图、期望 JSON、Markdown 和诊断
```

Android 不运行 Python Runtime。Windows 与 Android 通过版本化的数据契约、资源版本和 golden fixtures 对齐，而不是互相直接调用：

```text
                 Common Data Contract
                         |
             +-----------+-----------+
             |                       |
          Windows                 Android
```

进入业务迁移前，应先冻结 TaskManifest v7、CaptureSession v1、资源版本记录和契约样本，并让 Python 与 Kotlin 通过同一批 golden fixture。当前 Windows 行为应作为基准，迁移顺序为 `Domain -> Parser -> Merge -> Validation -> Renderer -> Storage -> UI`。

## 平台与发布边界

AccessibilityService 适合验证截图、节点滚动和跟随事件，但 Google Play 对非无障碍工具使用 Accessibility API 有声明、披露、同意和审核要求，不能把它当作 Play 版本的唯一依赖。MediaProjection 可以采集屏幕，但不负责滚动；Android 14 及更高版本还要求每次投影会话重新取得用户同意，并正确声明前台服务类型。

因此计划保留两条能力路径：

| 构建目标 | 主要能力 | 兜底 |
| --- | --- | --- |
| Internal / 个人 APK | Accessibility、悬浮球、自动长页面采集 | MediaProjection、手动跟随、相册导入 |
| Google Play | 合规声明后的能力、MediaProjection | 手动跟随、相册导入、分享导入 |

两条路径应只替换 `CaptureProvider`，不复制业务逻辑。方案文档中的平台政策和目标 API 需要在正式发布前重新核对。

## 开发顺序

推荐按以下出口推进：

1. P0：冻结数据契约、槽位边界、资源版本和发布目标。
2. P1：在真实设备验证悬浮窗、截图、权限拒绝、手动跟随和进程恢复。
3. P2：完成 `CaptureSession`、原图落盘、暂停/继续和恢复，不接 OCR。
4. P3：完成 Stability、Anchor、Coverage、Bottom 状态和长页面合成测试。
5. P4：在目标设备对比 Android OCR 与 Windows 基线，记录耗时、内存、温升和字段差异。
6. P5：迁移 Domain、Parser、Merge、Validator，并通过 golden fixtures。
7. P6：闭合 JSON、Markdown、历史替换、训练归档和分享导出。
8. P7：实现任务、悬浮入口、核对、结果和设置页面。
9. P8：完成 Pixel、Samsung、小米/HyperOS、荣耀、OPPO、vivo 等设备矩阵和发布合规检查。

最先验证的三个 Go / No-Go 是：悬浮球采集是否自然稳定、LongCapture 是否不漏中间区域、复杂真实盘口的机构/时序/冲突/证据是否完整可追溯。LongCapture 和数据完整性应先于视觉美化。

## MVP 边界

第一版聚焦：

- 本地采集
- OCR 识别
- 页面判型与槽位路由
- 多段合并
- 完整性和冲突校验
- 原图证据查看
- Markdown / JSON 输出

第一版不包含 AI 预测、在线盘口、云同步、账号体系、在线数据库、资源图形化编辑、多用户和高级投注玩法 UI。跨端同步也不进入 Android MVP；先保证数据契约和资源版本可识别。

## 构建与验证

在已安装 Android SDK、JDK 和可用 Gradle 环境的前提下，可使用项目自带 Wrapper：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

当前仓库只有示例应用，因此上述命令验证的是 Gradle 工程和模板测试；真正的业务验收还需要脱敏 golden fixtures、LongCapture 合成测试和真实设备测试。

## 相关文档

- [Android 版详细开发方案](FootballScreenshotOcr-Android版详细开发方案-补充完善版.md)：产品定位、业务不变量、架构、平台限制、跨端契约和验收标准。
- [Gradle 配置](build.gradle.kts)：根工程插件配置。
- [应用模块](app/build.gradle.kts)：当前 Android 模块、SDK 和依赖配置。
