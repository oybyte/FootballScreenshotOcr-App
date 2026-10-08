package com.fifa.ocr.feature.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference
import java.time.Instant

class AccessibilityCaptureService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceRegistry.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityServiceRegistry.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityServiceRegistry.detach(this)
        super.onDestroy()
    }

    fun captureFrame(callback: (CaptureResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(CaptureResult.Failure(CaptureFailure(CaptureFailureReason.SERVICE_UNAVAILABLE, "当前 Android 版本不支持无障碍截图。")))
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardwareBuffer = screenshot.hardwareBuffer
                try {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    if (bitmap == null) {
                        callback(CaptureResult.Failure(contentUnavailable()))
                        return
                    }
                    if (CaptureRules.isVisuallyBlank(bitmap)) {
                        bitmap.recycle()
                        callback(CaptureResult.Failure(contentUnavailable()))
                        return
                    }
                    callback(
                        CaptureResult.Success(
                            CapturedFrame(
                                bitmap = bitmap,
                                source = CaptureSource.ACCESSIBILITY,
                                capturedAt = Instant.now(),
                                displayWidth = bitmap.width,
                                displayHeight = bitmap.height,
                            ),
                        ),
                    )
                } catch (exception: RuntimeException) {
                    callback(
                        CaptureResult.Failure(
                            CaptureFailure(CaptureFailureReason.CONTENT_UNAVAILABLE, "屏幕内容不可用，请导入截图或重试。"),
                        ),
                    )
                } finally {
                    hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                callback(
                    CaptureResult.Failure(
                        CaptureRules.mapAccessibilityError(
                            errorCode = errorCode,
                            secureWindowErrorCode = ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
                            noAccessErrorCode = ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS,
                        ),
                    ),
                )
            }
        })
    }

    private fun contentUnavailable() = CaptureFailure(
        reason = CaptureFailureReason.CONTENT_UNAVAILABLE,
        message = "屏幕内容不可用，请导入截图或重试。",
    )

    companion object {
        const val SERVICE_ID = "com.fifa.ocr.feature.capture/.AccessibilityCaptureService"
    }
}

object AccessibilityServiceRegistry {
    @Volatile
    private var serviceReference: WeakReference<AccessibilityCaptureService>? = null

    fun attach(service: AccessibilityCaptureService) {
        serviceReference = WeakReference(service)
    }

    fun detach(service: AccessibilityCaptureService) {
        if (serviceReference?.get() === service) serviceReference = null
    }

    fun capture(callback: (CaptureResult) -> Unit) {
        val service = serviceReference?.get()
        if (service == null) {
            callback(
                CaptureResult.Failure(
                    CaptureFailure(CaptureFailureReason.SERVICE_UNAVAILABLE, "请先在系统设置中启用 FootballScreenshotOcr 无障碍服务。"),
                ),
            )
            return
        }
        service.captureFrame(callback)
    }

    fun isConnected(): Boolean = serviceReference?.get() != null
}

class AccessibilityCaptureProvider : CaptureProvider {
    override fun capture(callback: (CaptureResult) -> Unit) = AccessibilityServiceRegistry.capture(callback)
}
