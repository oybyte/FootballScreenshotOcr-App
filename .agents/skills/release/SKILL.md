# Release Skill

## 用途

准备 Internal APK、验证 Android 构建、记录版本和设备结果、生成可审计发布记录时使用。它不把构建成功等同于产品验收。

## 发布前事实

先读取根 `AGENTS.md`、当前 Plan、相关 Decision 和 v2.1 实施基线。确认：

- 构建目标是 Internal APK 还是未来 Play；
- 当前 `minSdk/targetSdk/compileSdk` 与计划是否一致；
- Schema、资源、OCR 模型和 Parser 版本是否可追溯；
- 是否存在未提交改动、未处理 migration 或 pending 风险。

当前仓库只有 `:app` Compose 示例，尚未具备 Hilt、Room、LongCapture、OCR 或跨端契约。不能生成“完整功能已发布”的表述。

## 必须执行的检查

在工具链可用时按实际目标执行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat lint
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
```

不存在的任务或未连接设备必须如实记录为 `pending`，不伪造通过。业务阶段还要执行对应 golden、migration、recovery、LongCapture 合成、OCR 对照和设备矩阵测试。

## 产物记录

每个可交付 APK/AAB 记录：

```text
Git commit
application/versionCode/versionName
build variant
APK/AAB SHA-256
min/target/compile SDK
schema version
resource versions and hashes
OCR model version and SHA-256
test commands and results
device/Android version results
known pending risks
```

原始截图、OCR 表格和任务完整数据不能上传到发布服务。分享和导出使用本地文件或 Android `content://` 授权，除非用户明确批准其他边界。

## 设备与权限

Internal 路线可以验证 Accessibility 截图/滚动、悬浮球、MediaProjection、手动跟随和恢复。MediaProjection 不提供滚动能力；Android 14+ 的用户同意、前台服务类型和会话生命周期必须真实验证。Play 发布不得把尚未审核的 Accessibility 能力写成保证功能。

至少记录 Pixel、Samsung、小米/HyperOS、荣耀、OPPO、vivo 等目标设备，以及旋转、锁屏、省电、后台冻结、低存储、权限拒绝、投影撤销、进程强杀和升级恢复结果。

## Git 与最终报告

发布前检查 diff、Plan、Decision、版本记录和工作区状态。提交信息应描述实际变更；不得为了发布自动重置或覆盖用户改动。最终报告区分：构建通过、测试通过、APK 生成、Internal 可安装、跨端兼容、Play 合规、deployed/live verified。没有证据的阶段保持 pending。
