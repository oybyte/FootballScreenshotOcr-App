# Decision: Room 作为任务事实源，Files 保存原图与导出

日期：2026-10-08
状态：Accepted
事实状态：P2 implemented

背景：

任务、槽位、采集会话、OCR 状态和冲突需要查询与恢复；原始截图、revision 和导出物适合以文件保存。Room 事务和文件复制不是同一个原子事务。

问题：

如何避免 Markdown/JSON 与任务状态形成第二数据库，并在进程强杀、低存储或部分写入后保留证据？

决定：

目标架构中 Room 是运行时任务事实源；Files 保存原图、revision、导出和训练归档；DataStore 只保存轻量设置。文件写入遵循 `pending -> file write -> validation -> commit -> reconciliation`，原图不放入 Room BLOB 作为唯一事实。禁止 destructive migration。

原因：

Room 适合任务元数据和状态查询，文件适合大图片和可移植导出；显式阶段和 reconciliation 能处理跨介质非原子写入，保护原始证据。

被拒绝的方案：

- 把 Markdown/JSON 当第二任务事实源；
- 用 DataStore 兜底存任务和 OCR；
- 以 `fallbackToDestructiveMigration` 换取快速升级；
- 写入失败时删除临时原图或静默重建任务。

影响：

P2 已引入 Room schema 1、`CaptureStore`、应用私有文件事务和启动 reconciliation；当前尚无历史数据库迁移。无法解释的文件/数据库不一致进入诊断或人工核对，不自动猜测归属。

验证与退出条件：

P2 已验证正常提交、各阶段故障注入、重复提交、缺图引用、孤儿/恢复扫描和 Session 中断恢复；旧版本 migration、真实低存储、数据库清空后的索引重建仍由后续 schema 变化和恢复专项补充。

相关文档：

- `FootballScreenshotOcr-Android版详细开发步骤-v2.1.md` 第 7、12 节
- `.agents/skills/storage-migration/SKILL.md`
