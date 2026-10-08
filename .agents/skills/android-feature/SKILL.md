# Android Feature Skill

## 用途

在实现 Android 页面、任务流程、导航、悬浮入口、核对页、结果页或设置页时使用。它只约束 Compose feature 的组织和验证，不替代 `long-capture`、`ocr`、`schema-change` 或 `storage-migration` 的专项规则。

## 开工前

1. 阅读根 `AGENTS.md`、本文件和目标模块的 `AGENTS.md`。
2. 确认当前模块真实存在；不要因为计划列出了 feature 就创建空壳模块。
3. 找到当前行为的 SSOT、导航入口、ViewModel、Repository 和相关测试。
4. 说明这次变更的 Change Classification；跨模块或行为变化建立 ExecPlan。

## 页面结构

每个导航目的地默认采用：

```text
Route -> ViewModel -> UiState -> Screen -> Components
```

建议文件形态：

```text
<feature>/
  <Destination>Route.kt
  <Destination>Screen.kt
  <Destination>ViewModel.kt
  <Destination>UiState.kt
  <Destination>Event.kt
  components/
```

`Route` 负责连接 `hiltViewModel()`、生命周期感知的 StateFlow 收集和导航回调；`Screen` 只接收不可变 `UiState` 与事件回调；`Components` 只处理展示和局部交互。子 Composable 不直接从容器获取 ViewModel。

## UDF 与状态

用户操作必须沿以下路径流动：

```text
UI Event -> ViewModel -> Repository / Service / Processor -> StateFlow -> Compose
```

- `UiState` 要能表达加载、空态、成功、失败、待核对和恢复中的状态。
- 一次性导航、分享或提示事件使用明确的事件流/消费机制，不把事件伪装成长期状态。
- 不从 Compose 状态直接生成 Markdown/JSON；结果页面读取已验证的领域快照。
- 不在 Composable 中访问 DAO、网络、文件、OCR 或 Android Service。
- 不在 UI 层猜测缺失值、吞掉诊断或把 `LIKELY_COMPLETE` 显示为完整成功。

## ViewModel 与 Repository

ViewModel 负责把用户事件转换为状态变化和调用；Repository 负责数据访问和领域边界。只有多个 ViewModel 共享复杂业务逻辑，或 ViewModel 已明显过重时才增加 UseCase，并在 ExecPlan 中说明原因。

能直接实现就不包 Interface。只在多个真实实现、Android/第三方边界、测试替身或明确的实现切换场景使用抽象。不要因为 Google 架构图或 Clean Architecture 习惯批量创建 `Interface + Impl`。

## Hilt

- 普通类优先 `@Inject constructor`。
- Room、DataStore、外部 SDK、复杂配置和实现绑定才使用 `@Module`、`@Provides` 或 `@Binds`。
- Hilt 根装配位于 `:app`，但 feature 不得通过 Service Locator 取依赖。
- 不引入 MVI、Redux、EventBus、自研 DI 或自定义状态框架。

## Navigation

导航目的地、参数和返回结果必须有稳定的数据契约。不要把大对象、Bitmap、OCR 表格或任务事实塞进路由字符串；通过 Repository/Room 读取事实，路由只传稳定 id 和小型显示参数。

新增目的地时同步更新导航图、深链/返回行为（如适用）、UI 测试和权限/错误路径。不能让页面绕过任务状态直接写文件或数据库。

## 测试与验证

- 纯状态转换和 ViewModel 逻辑优先单元测试。
- Screen 使用 Compose UI test 验证关键语义标签、事件和状态，不用截图通过率替代业务验证。
- 导航测试验证进入、返回、重复打开、进程恢复和参数缺失。
- Capture/OCR/Storage 行为使用对应专项 Skill，不在 UI test 中伪造“已完成”。
- 测试失败先判断代码、测试、环境或假设错误；不得修改 expected 让测试变绿。

## 完成门槛

功能只有在状态、错误、权限、恢复、无数据和待核对路径都真实表达，相关测试执行并通过，且 diff 不包含无关重构时才能标记完成。当前仓库仍只有 Compose 示例页；没有 Hilt/Room/Navigation 实现时，报告必须如实标记为未完成。
