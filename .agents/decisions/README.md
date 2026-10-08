# Architecture Decision Records

本目录只记录会影响模块边界、数据契约、持久化、平台能力或长期开发方向的架构决定。日常修复、单个 UI 调整和一次性排障写入 Git 历史或对应 Plan，不创建 Decision。

## 文件格式

文件名使用递增编号和短横线，例如 `0001-kotlin-compose-multimodule-hilt.md`。每份记录使用以下结构：

```markdown
# Decision: <标题>

日期：YYYY-MM-DD
状态：Proposed | Accepted | Superseded | Rejected
事实状态：Current | Planned | Pending verification

背景：

问题：

决定：

原因：

被拒绝的方案：

影响：

验证与退出条件：

相关文档：
```

`Planned` 表示已批准的目标方向，不表示代码已实现；`Pending verification` 表示缺少当前仓库、Windows consumer、契约或设备证据。修改已接受决定时必须新建或明确标记替代记录，不能静默编辑历史理由。

## 审查规则

- 决定必须引用真实事实、v2.1 基线、Schema、实现或测试。
- 说明对当前代码的差异、迁移成本、失败路径和验证门槛。
- 不用 Decision 代替执行计划，不把未验证的愿望写成现状。
- 决策之间发生冲突时，按根 `AGENTS.md` 的 Source of Truth 优先级和最新有效 Decision 处理，并在相关记录中交叉引用。

## 当前记录

- `0001-kotlin-compose-multimodule-hilt.md`：Android 技术栈和中等粒度模块。
- `0002-longcapture-and-capture-ocr-boundary.md`：LongCapture 三模式、Coverage 和 Capture/OCR 解耦。
- `0003-v7-cross-platform-contract.md`：v7 契约和 Windows 行为对齐边界。
- `0004-room-and-file-storage.md`：Room、Files、DataStore 和恢复事务。
- `0005-capture-provider-and-internal-release.md`：CaptureProvider、Accessibility/MediaProjection 边界和 Internal APK 首发。
