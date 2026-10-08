package com.fifa.ocr.feature.capture

import android.graphics.Bitmap
import java.time.Instant

enum class CaptureSource(val wireName: String) {
    ACCESSIBILITY("accessibility"),
    MEDIA_PROJECTION("media_projection"),
    MANUAL_IMPORT("manual_import"),
}

data class CapturedFrame(
    val bitmap: Bitmap,
    val source: CaptureSource,
    val capturedAt: Instant,
    val displayWidth: Int,
    val displayHeight: Int,
)

enum class CaptureFailureReason {
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    SECURE_WINDOW,
    CONTENT_UNAVAILABLE,
    TIMEOUT,
    INVALID_IMAGE,
    DECODE_FAILED,
    PROJECTION_STOPPED,
}

data class CaptureFailure(
    val reason: CaptureFailureReason,
    val message: String,
    val diagnosticCode: String? = null,
)

data class CapturePermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val overlayAllowed: Boolean,
    val projectionArmed: Boolean,
)

fun CapturePermissionSnapshot.accessibilityReady(): Boolean = accessibilityEnabled && accessibilityConnected

sealed interface CaptureResult {
    data class Success(val frame: CapturedFrame) : CaptureResult
    data class Failure(val failure: CaptureFailure) : CaptureResult
}

fun interface CaptureProvider {
    fun capture(callback: (CaptureResult) -> Unit)
}

enum class CaptureRoute {
    ACCESSIBILITY_ONLY,
    ACCESSIBILITY_THEN_PROJECTION,
    REQUEST_PROJECTION_CONSENT,
    REPORT_FAILURE,
}

object CaptureRules {
    fun mapAccessibilityError(errorCode: Int, secureWindowErrorCode: Int, noAccessErrorCode: Int): CaptureFailure =
        when (errorCode) {
            secureWindowErrorCode -> CaptureFailure(
                reason = CaptureFailureReason.SECURE_WINDOW,
                message = "CAPTURE_BLOCKED_BY_WINDOW_SECURITY",
                diagnosticCode = "CAPTURE_BLOCKED",
            )

            noAccessErrorCode -> CaptureFailure(
                reason = CaptureFailureReason.PERMISSION_DENIED,
                message = "无障碍截图权限不可用，请重新启用服务。",
            )

            else -> CaptureFailure(
                reason = CaptureFailureReason.SERVICE_UNAVAILABLE,
                message = "无障碍截图失败（错误码 $errorCode），可启用一次性屏幕投影后重试。",
            )
        }

    fun routeAfterAccessibilityFailure(failure: CaptureFailure, projectionArmed: Boolean): CaptureRoute = when {
        failure.reason == CaptureFailureReason.SECURE_WINDOW -> CaptureRoute.REPORT_FAILURE
        failure.reason == CaptureFailureReason.PERMISSION_DENIED -> CaptureRoute.REPORT_FAILURE
        projectionArmed -> CaptureRoute.ACCESSIBILITY_THEN_PROJECTION
        else -> CaptureRoute.REQUEST_PROJECTION_CONSENT
    }

    fun sharedImageUri(action: String?, mimeType: String?, streamUri: String?, clipUris: List<String>): String? {
        if (mimeType?.startsWith("image/") != true) return null
        if (action != "android.intent.action.SEND" && action != "android.intent.action.SEND_MULTIPLE") return null
        return streamUri?.takeIf(String::isNotBlank) ?: clipUris.firstOrNull(String::isNotBlank)
    }

    fun decodeSampleSize(width: Int, height: Int, maxEdge: Int = 4096, maxPixels: Long = 8_000_000): Int? {
        if (width <= 0 || height <= 0 || maxEdge <= 0 || maxPixels <= 0) return null
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE || width.toLong() * height > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (width / sample > maxEdge || height / sample > maxEdge ||
            (width.toLong() / sample) * (height.toLong() / sample) > maxPixels
        ) {
            sample *= 2
        }
        return sample
    }

    fun isVisuallyBlank(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return true
        val sampleColumns = minOf(bitmap.width, BLANK_SAMPLE_COLUMNS)
        val sampleRows = minOf(bitmap.height, BLANK_SAMPLE_ROWS)
        for (row in 0 until sampleRows) {
            val y = (row.toLong() * (bitmap.height - 1) / maxOf(1, sampleRows - 1)).toInt()
            for (column in 0 until sampleColumns) {
                val x = (column.toLong() * (bitmap.width - 1) / maxOf(1, sampleColumns - 1)).toInt()
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha > 8 && ((color shr 16) and 0xff) > 8 ||
                    alpha > 8 && ((color shr 8) and 0xff) > 8 ||
                    alpha > 8 && (color and 0xff) > 8
                ) return false
            }
        }
        return true
    }

    private const val MAX_SOURCE_EDGE = 24_000
    private const val MAX_SOURCE_PIXELS = 199_000_000L
    private const val BLANK_SAMPLE_COLUMNS = 40
    private const val BLANK_SAMPLE_ROWS = 40
}
