# ExecPlan 规范

ExecPlan 是可持续执行的工程文件，不是一次性聊天文本。它应让下一次会话能够从当前状态继续工作，并能看出为什么采用当前方案、哪些事实已经验证、什么条件下必须停下。

## 何时必须建立 Plan

以下情况必须建立或更新 Plan：

- 修改跨模块边界、数据契约、Room schema、文件事务或 Android 权限/Service。
- 修改 LongCapture、Coverage、OCR、Parser、Renderer 或跨端输出语义。
- 影响多个 feature、需要多个阶段或需要真实设备/跨端验证。
- 需要迁移、恢复、发布、设备矩阵或架构决策。
- 用户明确要求计划，或当前任务不能在一个小范围变更中完成。

T0 文档、单文件且无行为风险的小修正可以省略 Plan，但仍需在最终报告中说明检查范围。

## 文件命名与位置

Plan 放在仓库 `.agents/plans/` 下，文件名使用：

```text
YYYY-MM-DD-<short-kebab-name>.md
```

日期使用实际开始日期；不要覆盖旧计划。计划完成后保留在 Git 历史中，若仓库后续建立正式计划索引，再从索引指向它。

## 必备章节

每份 Plan 必须包含以下标题，内容不足时写明 `pending`，不得留空：

```text
# <Plan 标题>
## Goal
## Current State
## Invariants
## Scope
## Non-Scope
## Design
## Implementation Steps
## Compatibility
## Verification
## Risks
## Exit Criteria
```

### Goal

说明用户可观察的结果和完成边界。不要只写“重构模块”或“提高质量”。

### Current State

记录实际分支、相关文件、已有实现、测试、契约、环境限制和已发现差异。未知事实写 `pending`，不要用目标架构代替现状。

### Invariants

列出不可改变的产品规则、数据语义、证据链、输出格式、权限边界和已有兼容要求。引用 AGENTS、Schema、Windows 实现或 Golden fixture 的具体位置。

### Scope / Non-Scope

Scope 只列完成目标所必需的文件和行为。Non-Scope 必须明确不修改的模块、测试期望、无关重构、平台能力或后续阶段，防止执行过程中扩张。

### Design

说明数据流、状态、模块依赖和失败路径。对于 Android 页面使用 Route/ViewModel/UiState/Screen；对于 LongCapture 说明模式、Coverage 证据和熔断；对于存储说明事务和恢复；对于契约说明版本和迁移。不要把未验证假设写成事实。

### Implementation Steps

步骤必须有顺序、输入、输出和验证方式。执行中逐项更新状态，例如：`[ ]`、`[in progress]`、`[x]`、`[blocked]`。步骤发生变化时同时更新 Design、Risks 和 Exit Criteria，不能让计划落后于代码。

### Compatibility

说明对 Windows 行为、v6/v7 契约、Golden fixture、资源版本、已有任务、旧数据库、Android 版本、Play/Internal 构建的影响。没有真实 consumer 或 fixture 时写明阻塞条件。

### Verification

按 T0–T6 选择实际命令和测试。区分静态检查、单元测试、集成测试、真机测试、跨端对照和人工核对。只有真正执行过的检查才标记通过；环境无法执行时标记 `pending` 并说明原因。

### Risks

列出会导致数据丢失、证据断链、伪完整、兼容性回归、权限不可用或无法恢复的风险，并写出监测与缓解方式。

### Exit Criteria

使用可观察的门槛，例如 false-complete 为 0、冲突双向保留率 100%、字段可回溯、迁移可恢复、fixture 对照通过、构建产物已记录。不要使用“看起来正常”“大致完成”。

## 执行规则

1. 开工前先读根 `AGENTS.md`、相关 Skill 和模块级规则，再创建/更新 Plan。
2. 计划中的每个状态必须对应代码、命令、测试或审查证据。
3. 发现与计划不符时先停在当前步骤，记录差异和影响，再决定是否需要用户裁决或 Decision。
4. 如果触发红线、缺少关键 SSOT、无法保护原图/冲突/契约，必须停止实现并报告原因。
5. 不得通过扩大 Scope、修改 expected、关闭测试或降低门槛来满足 Exit Criteria。
6. 合并前检查 Plan、代码、测试、文档和 Git diff 是否一致；未完成的 Plan 不能标记完成。

## 必须停止的情况

遇到以下情况必须暂停依赖步骤并报告：

- 需要修改产品硬规则、未知 Schema、Windows 行为或 Golden fixture，但找不到权威来源。
- 只能通过丢弃原图、原始 OCR、冲突值、诊断或历史版本来继续。
- 权限、媒体投影、无障碍或网络能力与当前发布目标不符。
- Room migration、文件写入或恢复路径无法证明不会丢失任务事实。
- 测试失败原因未区分代码错误、测试错误、环境错误和假设错误。
- 需要越出用户授权范围，或需要破坏性删除、重命名、覆盖未提交改动。

## 计划审查清单

完成 Plan 前确认：

- Goal 可以由用户观察和复现；
- Current State 没有把目标架构写成现状；
- Invariants 覆盖业务红线和证据链；
- Scope/Non-Scope 能阻止 opportunistic refactor；
- Design 包含失败路径和恢复；
- Verification 包含真正要运行的门禁；
- Exit Criteria 可以用证据判定；
- 相关架构决定已链接到 `.agents/decisions/`。
