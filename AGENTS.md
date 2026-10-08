# Agent 工作规范

本文件是 `FootballScreenshotOcr` 仓库的长期规则入口。它只记录跨任务都必须遵守的边界；专项流程放在 `.agents/skills/`，阶段计划格式放在 `.agents/PLANS.md`，架构决策放在 `.agents/decisions/`。

## 项目定位

FootballScreenshotOcr 是本地优先的足球盘口截图采集与结构化系统。Windows/Python 是既有行为基准，Android/Kotlin 是新的客户端实现。Android 复制业务行为和数据语义，不复制 Python、PySide6、Windows 路径、Windows 服务或 Windows Runtime。

Android 的产品角色是盘口页面旁的移动采集副驾：采集当前屏幕或连续页面，保留原始证据，完成 OCR、判型、分槽、合并、校验并输出 Markdown/JSON。它不负责比赛预测。

## 当前检出状态

规则必须区分“目标架构”和“当前事实”：

- 当前仓库只有 `:app` Android Gradle 工程和 Compose 示例入口；业务模块、Hilt、Room、OCR、Parser、LongCapture 尚未实现。
- 当前 `app` 的 `minSdk` 是 24；v2.1 基线目标是 `minSdk 30`、`compileSdk 36`、`targetSdk 36`。不得把目标值写成当前已实现事实，也不得在无关任务中顺手修改 Gradle。
- 当前检出没有 Windows/Python 源码、Windows 测试、`resources/`、`contracts/` 或 golden fixtures。需要它们时必须先定位真实来源并记录差异，不能凭旧文档补造。
- v2.1 是 Android 实施基线；与代码不一致时遵循“事实检查、明确记录差异、再按任务处理”，不得静默改变业务规则。

## 产品红线

任何模块、脚本、网络请求和导出流程都必须遵守：

- 不预测比赛，不提供投注建议。
- 不上传截图、OCR 盘口表、任务完整数据、prompt 或盘口明细。
- 不猜测缺失值，不把网络失败解释为“无数据”。
- 不静默覆盖冲突，不静默截断，不把部分采集伪装成完整成功。
- 保留原始图片、原始 OCR 文本、证据坐标、来源和 OCR/解析告警。
- 网络只允许用于已批准的体彩/500.com 身份查询，并且必须经过 `FixtureResolver` 边界。

## Source of Truth 优先级

发生冲突时按以下顺序裁决：

```text
当前用户明确任务要求
>
产品硬规则
>
版本化 JSON Schema / Contract
>
Windows 当前实现和测试
>
Golden Fixtures
>
Android 架构规范
>
Agent 自己推断
```

Agent 的“更优设计”不得覆盖更高优先级事实。某一层当前不存在时，必须标记为 `pending` 或 `out-of-scope`，不得用猜测替代。

## Android 目标架构

目标路线是 Kotlin + Jetpack Compose + ViewModel + StateFlow + UDF + Navigation Compose + Hilt + Room + DataStore + Coroutines + ONNX Runtime Android。采用中等粒度多模块，不为架构图继续拆分几十个微型模块。

目标模块按真实职责逐步建立：

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

模块尚未存在时不要创建空壳规则或空壳代码。模块创建后，只有它有独立边界时才增加模块级 `AGENTS.md`。

### Compose 与状态

页面默认遵循：

```text
Route -> ViewModel -> UiState -> Screen -> Components
UI Event -> ViewModel -> Repository / Service / Processor -> StateFlow -> Compose
```

`Screen` 接收 `UiState` 和事件回调。Composable 不直接访问 Room、DAO、网络或 Service，不在每个子 Composable 中创建 ViewModel，不把业务规则放入 UI。只有独立导航目的地才在 Route 层获取对应 ViewModel。

### Hilt、UseCase 与抽象

- 普通对象优先 `@Inject constructor`。
- 只有 Room、OkHttp、DataStore、外部 SDK、复杂配置或实现绑定才使用 `@Provides` / `@Binds`。
- 不为习惯预创建 UseCase；只有多个 ViewModel 共享复杂逻辑，或 ViewModel 已明显过重时才引入，并在计划中写明原因。
- 能直接实现就不增加抽象。Interface 只用于多个真实实现、Android/第三方边界、测试替身或明确的实现切换，例如 `CaptureProvider`、`FixtureResolver`。
- 禁止 MVI/Redux 框架、EventBus、Service Locator、自研 DI、大量 `Interface + Impl`、`api/impl` 双模块和为单一 class 创建 module。

## LongCapture 与 Coverage

LongCapture 是 Android 一级能力，必须区分 `AUTO`、`FOLLOW`、`MANUAL`。`FOLLOW` 的语义是“用户滑动、页面停止、应用自动采集”，不能被实现成一次点击后由程序接管全部滚动。

LongCapture 必须覆盖：顶部确认、底部确认、稳定检测、实测位移、overlap、anchor、gap、coverage、暂停、继续、恢复、重放和熔断。必须持久化 `CaptureSession`、`CaptureSegment`、滚动请求、观察到的位移、连接证据和诊断。

以下做法禁止：

- 用固定滑动距离或段数证明页面完整。
- 未确认顶部、未确认底部或存在疑似 gap 时输出 `COMPLETE`。
- 把多张截图简单拼成巨大 Bitmap 作为唯一数据表示。
- 页面持续无进展时无限滚动。

Coverage 只允许使用这些状态：`NOT_APPLICABLE`、`NOT_RECORDED`、`COMPLETE`、`LIKELY_COMPLETE`、`GAP_SUSPECTED`、`INCOMPLETE`。只有顶部、底部、所有相邻段连接且无未解释 gap 才能为 `COMPLETE`；还必须通过数据完整性门禁才能自动完成槽位。

LongCapture 必须有 `maxSegments`、`maxScrollAttempts`、`maxNoProgress`、`maxStabilityWait`、`maxSessionDuration`。触发熔断后停止并进入 `REVIEW_REQUIRED`，保留已采集原图和 replay 信息。

## Capture、OCR、证据与冲突

采集和 OCR 解耦：

```text
Capture -> Segment 持久化 -> OCR Queue
```

原图可靠保存后，采集才可继续滚动；Capture 不等待完整 OCR。OCR 采用 CPU 兼容基线、单队列 Worker 和审核过的打包模型，不运行时下载模型。必须记录模型版本、模型 SHA-256、预处理版本、原始 OCR 文本、bbox、置信度和诊断；纠错不得丢弃原始 OCR 证据。

所有截图派生字段保留 `asset_id + 原始图片 + 原始 bbox`。非截图字段使用自己的 `SourceReference`，不要强行要求 bbox。相同语义位置出现不同值时，保留双方值、来源、bbox（如有）并标记 `Conflict`，禁止静默选择一方。

## Storage 与网络

目标架构中 Room 是运行时任务事实源，保存 Task、身份、Slot、Asset 元数据、CaptureSession/Segment 元数据、OCR 状态、Conflict、Revision 和资源版本。Files 保存原图、revision 和导出物；DataStore 只保存轻量设置。Markdown/JSON 是派生输出，不是第二任务数据库。

禁止 `fallbackToDestructiveMigration`。写入遵循：

```text
pending -> file write -> validation -> commit -> reconciliation
```

任何失败都保留可恢复原图。网络 Resolver 只能查询允许的身份信息；网络失败不得制造数据、覆盖人工值或伪造来源。单元测试不得访问真实网络。

## Agent 工作流

复杂任务必须执行：

```text
Read AGENTS.md
-> Read relevant Skill
-> Inspect repository
-> Find SSOT
-> Find relevant tests / fixtures
-> Define scope
-> Create or update ExecPlan
-> Implement
-> Test
-> Review diff
-> Report
```

小型、局部且无行为风险的文档或 UI 修正可省略 ExecPlan，但仍需读本文件和相关局部规则。

开始修改代码前必须回答：

1. 要修改什么？
2. 当前行为在哪里定义？
3. SSOT 是什么？
4. 哪些行为绝对不能改变？
5. 用什么测试证明没有回归？

不得进行 opportunistic refactor。不得为通过测试修改 expected、删除失败测试、降低验收阈值、关闭 lint/测试或把构建通过当成完整完成。

## Change Classification

| 分类 | 含义 | 最低验证 |
|---|---|---|
| T0 | 文档/规则 | Markdown、链接和规则一致性检查 |
| T1 | UI-only | Compose 编译与相关 UI 测试 |
| T2 | 普通实现 | 单元测试、必要的集成测试 |
| T3 | 行为变化 | 回归测试、Golden fixture |
| T4 | Schema/Contract | 契约、迁移和 Windows 兼容测试 |
| T5 | Storage/Migration | Migration、恢复、崩溃/重启测试 |
| T6 | 跨端兼容 | 跨端 fixture、JSON/Markdown 对照 |

专项执行方式见 `.agents/skills/verification/SKILL.md`。阶段计划必须遵循 `.agents/PLANS.md`。架构变化必须记录在 `.agents/decisions/`；日常修复不创建 Decision。

## 当前规则边界

本文件不会授权 Agent 修改业务代码、测试期望、Gradle、Manifest、数据库、模型或既有产品文档。任务若要求改变已冻结决策（Kotlin、Compose、Multi-Module、Hilt、Room、StateFlow、UDF、Navigation Compose、目标 API、Internal APK、LongCapture、三种采集模式），必须单独提出架构决策并记录原因、影响和迁移方案。
