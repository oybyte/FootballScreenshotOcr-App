# `:app` 模块规则

本规则只补充根 `AGENTS.md`，不降低根规则。`:app` 是 Android application 和 Compose 示例入口；P0A 已建立 `:core`、`:data`、`:ocr`、`:parser` 与 feature library 模块。模块骨架已可构建，但不能据此假设 Hilt、Room 或产品业务流程已接线。

## 职责

`:app` 负责：

- Application、MainActivity 和全局 Compose 主题；
- Navigation Compose 的总装配；
- Hilt 根组件和跨模块依赖装配；
- Feature 组合、权限协调、Service/Overlay 的 Android 入口；
- 应用级错误、启动恢复和生命周期连接。

`:app` 不负责：

- Parser、SlotRouter、TimelineMerger、Validator 等业务算法；
- OCR 预处理、模型推理和证据映射；
- LongCapture 的 Coverage、Anchor、Gap 或熔断算法；
- 直接写 SQL、实现 Room 细节或拼接 Markdown/JSON；
- 在 Activity/Composable 内绕过 Repository 读取或写入任务事实。

## Compose 约束

页面按以下结构连接：

```text
Route -> ViewModel -> UiState -> Screen -> Components
```

Screen 只接收 State 和事件回调；Composable 不直接获取 DAO、网络、Service 或业务依赖。导航参数传稳定 id，不传 Bitmap、完整盘口表或任务事实。子 Composable 不自行创建 ViewModel，除非它是独立导航目的地。

## Hilt 与 Service

普通对象优先构造器注入。只有 Room、DataStore、第三方 SDK、复杂配置或真实实现绑定才在 `:app` 建 Module。Service 和 Overlay 应保持薄：负责 Android 生命周期、权限、通知和事件转发，业务状态必须由 Repository/持久化层保存，不能只放在 Service 内存。

Accessibility、MediaProjection、悬浮窗和前台服务权限拒绝必须有可观察的状态和降级路径。`:app` 不得把 MediaProjection 当成滚动器，也不得把 Internal APK 能力写成 Play 已保证能力。

## 验证

涉及导航或页面状态时运行相关 Compose/UI 测试；涉及权限、Service、恢复或跨模块装配时增加集成/真机验证。只改示例 UI 也要确认无关业务模块和规则未被引入。测试 expected 不得为绿色结果而改写。
