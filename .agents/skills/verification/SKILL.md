# Verification Skill

## 用途

根据 Change Classification 选择并执行验证，分析失败，审查 diff，防止伪通过。它适用于代码、Schema、存储、LongCapture、OCR、跨端兼容、规则和发布前检查。

## 证据原则

- 只有实际执行的命令、测试、设备操作或人工审查才算证据。
- “能编译”“页面打开”“一次真实演示”不能单独证明业务完成。
- 未执行、环境不可用或缺少依赖时写 `pending`，不写“已通过”。
- 不得修改 expected、删除失败测试、关闭 lint/测试或降低验收阈值制造通过。

## 按分类选择门禁

| 分类 | 必须检查 |
|---|---|
| T0 文档/规则 | Markdown 语法、链接、路径引用、规则一致性、Git diff |
| T1 UI-only | 编译、Preview/Compose UI test、无数据/错误/返回路径 |
| T2 普通实现 | 单元测试、相关集成测试、失败路径、静态检查 |
| T3 行为变化 | 回归测试、Golden fixture、告警/冲突/输出差异 |
| T4 Schema/Contract | Schema 校验、迁移、往返、Windows/Kotlin 对照 |
| T5 Storage/Migration | Room migration、文件事务、强杀/重启、reconciliation |
| T6 跨端兼容 | 共同 fixture、JSON/Markdown 结构、资源/模型版本 |

## 失败分析顺序

测试失败时先记录：

```text
代码错误？
测试或 expected 错误？
环境/设备/权限错误？
任务假设与当前事实不符？
```

再决定修复代码、修复测试、修复环境或暂停等待用户/上游契约。不得默认测试应该改变，也不得用重试隐藏不稳定。

## LongCapture / OCR / Evidence 专项

- LongCapture：缺中段、未知顶部、未知底部、无锚点、重复段、动态区域、用户暂停和熔断；false-complete 必须为 0。
- OCR：模型版本和 hash、预处理版本、原始文本、bbox、置信度、耗时、内存、温升和失败保留原图。
- Evidence：每个截图字段可回到 asset 和原图 bbox；越界映射产生告警；冲突双方均保留。
- Storage：每个写入阶段可恢复；进程强杀、低存储和数据库/文件不一致不能生成完整成功。

## Diff 审查

验证前后检查：

1. `git status` 和 `git diff --stat` 是否只包含 Scope；
2. 是否误改产品文档、测试期望、Gradle、Manifest 或模型；
3. 新增依赖、接口、模块和配置是否都有真实理由；
4. 日志、错误、空态、权限拒绝和恢复是否真实表达；
5. Plan、Decision、规则和实现是否一致。

最终报告必须列出执行过的命令、结果、未执行项和剩余风险。不得以“应该通过”替代命令结果。
