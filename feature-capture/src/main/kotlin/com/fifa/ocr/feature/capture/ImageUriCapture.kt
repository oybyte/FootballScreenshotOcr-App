package com.fifa.ocr.feature.capture

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.IOException
import java.time.Instant

class ImageUriCapture(private val contentResolver: ContentResolver) {
    fun decode(uri: Uri): CaptureResult {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return failure(CaptureFailureReason.PERMISSION_DENIED, "无法读取所选图片，请重新选择。")
            val sampleSize = CaptureRules.decodeSampleSize(bounds.outWidth, bounds.outHeight)
                ?: return failure(CaptureFailureReason.INVALID_IMAGE, "图片尺寸无效或超过 1.6 亿像素上限。")
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return failure(CaptureFailureReason.DECODE_FAILED, "图片解码失败，请选择其他图片。")
            CaptureResult.Success(
                CapturedFrame(
                    bitmap = bitmap,
                    source = CaptureSource.MANUAL_IMPORT,
                    capturedAt = Instant.now(),
                    displayWidth = bitmap.width,
                    displayHeight = bitmap.height,
                ),
            )
        } catch (_: SecurityException) {
            failure(CaptureFailureReason.PERMISSION_DENIED, "图片访问权限已失效，请重新选择或分享图片。")
        } catch (_: IOException) {
            failure(CaptureFailureReason.DECODE_FAILED, "图片读取失败，请选择其他图片。")
        } catch (_: RuntimeException) {
            failure(CaptureFailureReason.DECODE_FAILED, "图片解码失败，请选择其他图片。")
        }
    }

    private fun failure(reason: CaptureFailureReason, message: String) =
        CaptureResult.Failure(CaptureFailure(reason, message))
}
