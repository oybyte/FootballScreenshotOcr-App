# OCR Skill

## 用途

实现 Android OCR、预处理、队列、模型封装、证据映射、性能基线和 OCR 诊断时使用。它不负责页面滚动、槽位业务规则或 Room migration。

## 边界与基线

目标结构保持简单：

```text
OcrEngine
ImagePreprocessor
OcrQueue
EvidenceMapper
Diagnostics
```

Android 使用 ONNX Runtime Android；CPU 是兼容性基线，NNAPI 只能作为经过实测的可选优化，不能成为新架构依赖。模型必须随受控资源版本打包，禁止运行时下载未经审核的模型。

Capture 与 OCR 解耦：原始 `CaptureSegment` 先提交，再进入队列。OCR 处理不得阻塞下一次滚动，也不得以 OCR 成功作为保留原图的前提。

## 模型与资源记录

每个 OCR 结果必须记录：

```text
model_id
model_version
model_sha256
preprocess_version
runtime/version
device information
```

模型、布局、机构规则和 prompt/template 的版本要可追溯。资源更新需重新打包并在结果/诊断中留下实际版本；不得从开发机绝对路径读取资源。

## 预处理

预处理要明确输入色彩、尺寸、裁剪、缩放、灰度/对比度、数字精修和坐标变换。所有派生图都记录变换参数，并把识别 bbox 映射回原始截图坐标。不能为了提高通过率静默裁掉无法映射的区域；映射失败必须产生诊断并保留原图 bbox（如有）。

Android 屏幕比例和 DPI 与 Windows 不同，不能直接复用固定像素坐标。布局使用版本化的归一化区域或经过验证的设备适配，不能把加载同一 YAML 当作解析行为等价。

## Queue 与失败

默认使用单 OCR Worker，状态至少包括：

```text
WAITING -> RUNNING -> SUCCEEDED
                    -> FAILED
                    -> RETRY
```

队列必须可暂停、恢复、重试和诊断。重试应有限制并记录原因、耗时和次数。OCR 失败时保留 `ImageAsset`、`CaptureSegment`、原始文本（如有）、错误码和可读的失败说明；界面提供重新识别和查看原图，不让 Slot 消失。

## 证据

OCR 输出至少包含原始文本、结构化候选、bbox、置信度和段/资源引用。纠错、归一化或 Parser 选择值时不得丢弃原始 OCR 候选。截图字段必须可追溯到：

```text
asset_id + 原始图片 + 原始归一化 bbox
```

非截图字段使用明确的 `SourceReference`。同一语义位置的不同候选交由 Conflict 规则处理，OCR 不得静默选择或覆盖。

## 性能与安全

在目标设备记录 OCR p50/p95、单段耗时、峰值内存、温升、磁盘占用和队列恢复耗时。避免一次性并行加载多段图、Tensor 和中间 Bitmap。日志不得包含截图内容、完整盘口明细或用户隐私；只记录诊断所需的 id、版本、计时和错误。

## Golden 与对照

使用脱敏 Golden fixture 对比 Windows 基线，至少检查中文机构名、负号、盘口分数、时间、数字、bbox 和警告语义。对照失败时先判断模型、预处理、布局、Parser、fixture 或环境原因；不得修改 expected、放宽阈值或用未审核模型“救通过率”。

只有真实执行了模型版本、设备和测试记录，才能声称 OCR 对照通过。无法运行 ONNX 或缺少模型时，报告为 `pending`，不能以编译通过替代。
