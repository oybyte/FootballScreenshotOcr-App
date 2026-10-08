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

当前仓库仍只有 `:app` 和 Compose 示例，Hilt、Navigation 与其他模块尚未实现。目标 `minSdk` 是 30，但当前 Gradle 仍为 24；后续实现任务必须单独处理该差异。

验证与退出条件：

模块依赖通过构建检查；Feature 的 State/Event/Navigation/UI test 可独立验证；不存在业务逻辑进入 Composable 或 `:app` 算法实现。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 2–7、18 节
- `AGENTS.md`
- `.agents/skills/android-feature/SKILL.md`
