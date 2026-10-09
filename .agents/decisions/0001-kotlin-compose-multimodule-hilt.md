# Decision: Kotlin Compose 与中等粒度多模块 Hilt

日期：2026-10-08
状态：Accepted
事实状态：Planned

背景：

Android 版既要复现 FootballScreenshotOcr 的业务行为，也承担学习现代 Jetpack Compose/Android 架构的目标。v2.1 将 Kotlin、Compose、ViewModel、StateFlow、UDF、Navigation Compose、Hilt 和中等粒度多模块列为实施基线。

问题：

如何组织 Android 工程，既能隔离采集、OCR、Parser、数据和 Feature，又不因追求架构完整而产生大量空壳模块和抽象层？

决定：

采用 Kotlin + Jetpack Compose + ViewModel + StateFlow + UDF + Navigation Compose + Hilt。按真实职责逐步建立 `:app`、`:core`、`:data`、`:ocr`、`:parser` 和必要的 `:feature-*` 模块。页面使用 `Route -> ViewModel -> UiState -> Screen -> Components`，普通依赖优先构造器注入。

原因：

这条路线符合当前 Android 学习目标和 v2.1 基线，同时允许纯 Kotlin Parser 与 Android 平台代码分离。中等粒度能控制依赖方向，避免几十个微型模块。

被拒绝的方案：

- 继续使用单一大模块承载全部业务；
- MVI/Redux/EventBus 或自研 DI；
- 为每个 class 建立 module、Interface 和 Impl；
- 机械复制 Clean Architecture 的 api/impl/data-source 层级。

影响：

P0A 已建立 `:app`、`:core`、`:data`、`:ocr`、`:parser` 和五个 `:feature-*` 模块，且模块边界验证通过。P1 使用 Compose 工作台和显式装配，但 Hilt、Navigation、ViewModel/StateFlow、Room 和完整业务流程仍未实现。当前 `minSdk=30`、`compileSdk=36`、`targetSdk=36` 已生效；本 Decision 的目标方向仍需随后续模块实现逐步验证。

验证与退出条件：

模块依赖通过构建检查；Feature 的 State/Event/Navigation/UI test 可独立验证；不存在业务逻辑进入 Composable 或 `:app` 算法实现。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 2–7、18 节
- `AGENTS.md`
- `.agents/skills/android-feature/SKILL.md`
