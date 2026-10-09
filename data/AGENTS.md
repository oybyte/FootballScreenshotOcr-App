# `:data` 模块规则

`:data` 是 Android 运行时任务事实源边界，依赖 `:core`，不得依赖任何 feature 模块。

- Room 只保存任务、槽位、资产、Session/Segment、诊断和 revision 元数据；禁止保存 Bitmap 或图片 BLOB。
- 原图必须通过 `CaptureFileStore` 写入应用私有 `captures/pending` 和 `captures/committed`，遵循 pending、临时文件、校验、原子改名、Room 提交的顺序。
- 文件和 Room 不共享原子事务；失败时保留文件和状态，reconciliation 必须幂等且不得静默删除孤儿文件。
- 对外只提供 `CaptureStore` 等有真实实现切换或测试替身价值的边界；不要新增 api/impl 模块或为单一 DAO 创建 UseCase。
- v7 枚举和会话状态来自 `:core`，不要在本模块复制或扩展业务枚举。
