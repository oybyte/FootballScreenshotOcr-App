# FootballScreenshotOcr

FootballScreenshotOcr 是一个面向足球盘口截图的本地化采集与结构化工具。Android 版的目标不是做一个普通的图片 OCR 应用，而是成为盘口页面旁边的“移动采集副驾”：用户继续在新球体育或其他盘口应用中查看页面，FootballScreenshotOcr 通过悬浮入口、当前屏幕采集、长页面采集、相册导入和系统分享入口获取截图，然后完成 OCR、数据合并、完整性检查和结果导出。

本项目强调准确、诚实、可复现和可追溯。应用不预测比赛、不提供投注建议、不自动上传数据、不猜测缺失值，也不静默覆盖冲突数据；每个识别结果都应能回溯到原始截图及其坐标。

## 当前状态

当前仓库已完成 P0A 工程基线和 P1 单屏采集 Spike 的代码实现。P1 工作台支持无障碍截图、悬浮入口、MediaProjection 降级、图片选择器、系统分享导入和安全窗口诊断；结果只保留在当前进程内存中，不写任务、原图或导出文件。自动化构建和 `emulator-5554` instrumentation 已通过，但投影撤销、权限撤销、分享导入等完整手工矩阵仍为 `pending`，不能据此宣称真实 OEM 或 Google Play 已验证。

当前可确认的工程事实：

- 应用模块为 `app`，包名为 `com.fifa.ocr`。
- 当前入口是 Jetpack Compose P1 屏幕采集工作台，包名为 `com.fifa.ocr`。
- `minSdk` 为 30，`compileSdk` 和 `targetSdk` 为 API 36；版本统一登记在 `gradle/libs.versions.toml`。
- 当前工程包含 `:app`、`:core`、`:data`、`:ocr`、`:parser`、`:feature-task`、`:feature-capture`、`:feature-review`、`:feature-result` 和 `:feature-settings`。模块边界可构建，但 Hilt、Room、OCR、Parser 业务流程、Navigation 和 LongCapture 尚未接线。
- 方案第 76 节仍按“仓库只有 Python / PySide6 Windows 应用”的历史前提描述交付边界；当前检出实际是 Android Gradle 工程，后续不应机械地再创建一个重复的 `android/` 根工程。
- `contracts/` 和 `test-fixtures/golden/` 已由 Android 仓库维护；Windows/Python 运行时仍在独立仓库，完整 v7 读写和 Renderer 支持属于 P0B。
- 工作台展示的权限与运行状态生命周期不同：无障碍开关和悬浮窗授权由 Android 系统保存；Accessibility 连接、悬浮入口服务和 MediaProjection 会话是运行时状态，进程/服务结束后需要重新建立，其中 MediaProjection 每次会话都必须重新取得用户同意。当前页面初始状态先填 `false`、再于 `onResume()` 查询系统，重启时可能短暂显示“未授权”；这属于待修复的状态呈现问题，不代表系统授权必然丢失。
- Android 详细方案见 [FootballScreenshotOcr-Android版详细开发方案-补充完善版.md](FootballScreenshotOcr-Android版详细开发方案-补充完善版.md)。

Android UI 采用 Compose，与根 `AGENTS.md` 和 Decision 0001 一致；方案文档中关于原生 View 的早期建议不再作为当前实现要求。

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

上述 OCR、分槽、合并、校验和导出目前属于目标流程，不是 P1 已交付能力。当前 P1 只实现单屏采集、图片导入和内存预览。

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

目标架构中 Room 是任务运行事实源，Files 保存原图、revision 和导出物，DataStore 只保存轻量设置。P1 尚未引入持久化：当前预览 Bitmap 只在内存中保留，进程重启后自然丢失。P2 才处理 `CaptureSession`、原图事务、哈希去重、暂停/继续和进程恢复；任何文件提交失败都必须保留可恢复原图，不能生成“完整成功”结果。

“复制给 AI”继续使用“分析指令全文 + 盘口数据”的格式，并排除赛果内容。应用只在本地处理数据，不自动上传。

## 目标架构

当前模块边界如下：

```text
:app             Activity、Compose UI、悬浮层、Service、权限入口
:core            v7 契约、迁移和共享纯 Kotlin 模型
:data            Android data 层骨架
:ocr             OCR 模块骨架
:parser          纯 Kotlin Parser 模块骨架
:feature-task    任务模块骨架
:feature-capture 采集入口和 provider
:feature-review  核对模块骨架
:feature-result  结果模块骨架
:feature-settings 设置模块骨架
contracts/       Android 唯一维护的 v7 Schema
test-fixtures/   脱敏 Golden fixture 和期望输出
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

1. P0：冻结数据契约、槽位边界、资源版本和发布目标（P0A 已完成，P0B Windows v7 运行时仍待做）。
2. P1：完成单屏采集 Spike；当前模拟器自动化已通过，完整手工验收仍待收尾。
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

当前可执行的主要门禁为：

```powershell
.\gradlew.bat clean test assembleDebug verifyModuleBoundaries --no-configuration-cache
.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache
```

前者验证十模块编译、单元测试和依赖边界；后者在 `emulator-5554` 上验证 Compose、安全窗口和图片导入 instrumentation。P2 之前不宣称 Room、OCR、LongCapture、业务导航或真实设备兼容已完成。

## 相关文档

- [Android 版详细开发方案](FootballScreenshotOcr-Android版详细开发方案-补充完善版.md)：产品定位、业务不变量、架构、平台限制、跨端契约和验收标准。
- [Gradle 配置](build.gradle.kts)：根工程插件配置。
- [应用模块](app/build.gradle.kts)：当前 Android 模块、SDK 和依赖配置。
