package com.fifa.ocr.feature.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.time.Instant

object CaptureServiceActions {
    const val ACTION_CAPTURE_REQUESTED = "com.fifa.ocr.feature.capture.CAPTURE_REQUESTED"
    const val ACTION_START_FLOATING = "com.fifa.ocr.feature.capture.START_FLOATING"
    const val ACTION_STOP_FLOATING = "com.fifa.ocr.feature.capture.STOP_FLOATING"
    const val ACTION_START_PROJECTION = "com.fifa.ocr.feature.capture.START_PROJECTION"
    const val ACTION_STOP_PROJECTION = "com.fifa.ocr.feature.capture.STOP_PROJECTION"
    const val ACTION_PROJECTION_STATE = "com.fifa.ocr.feature.capture.PROJECTION_STATE"
    const val ACTION_FLOATING_STATE = "com.fifa.ocr.feature.capture.FLOATING_STATE"
    const val EXTRA_PROJECTION_ARMED = "projection_armed"
    const val EXTRA_FLOATING_RUNNING = "floating_running"
    const val EXTRA_FAILURE_MESSAGE = "failure_message"
    const val EXTRA_FAILURE_REASON = "failure_reason"
    const val EXTRA_RESULT_CODE = "projection_result_code"
    const val EXTRA_RESULT_DATA = "projection_result_data"
}

class MediaProjectionCaptureProvider : CaptureProvider {
    override fun capture(callback: (CaptureResult) -> Unit) {
        if (!ProjectionCaptureService.captureFrame(callback)) {
            callback(
                CaptureResult.Failure(
                    CaptureFailure(CaptureFailureReason.PERMISSION_DENIED, "请先返回应用并启用一次性屏幕投影，再从悬浮入口重试。"),
                ),
            )
        }
    }

    fun isArmed(): Boolean = ProjectionCaptureService.isArmed()
}

class FloatingCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        instance = this
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            attachFloatingView()
        } catch (_: RuntimeException) {
            sendBroadcast(
                Intent(CaptureServiceActions.ACTION_FLOATING_STATE)
                    .setPackage(packageName)
                    .putExtra(CaptureServiceActions.EXTRA_FLOATING_RUNNING, false)
                    .putExtra(CaptureServiceActions.EXTRA_FAILURE_MESSAGE, "悬浮入口无法显示，请检查悬浮窗权限后重试。"),
            )
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CaptureServiceActions.ACTION_STOP_FLOATING -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeFloatingView()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun attachFloatingView() {
        if (floatingView != null) return
        val button = TextView(this).apply {
            text = "截"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "采集当前盘口屏幕"
            setBackgroundResource(android.R.drawable.btn_default)
            setOnTouchListener(DragTouchListener())
            setOnClickListener {
                setVisible(false)
                mainHandler.postDelayed({
                    sendBroadcast(Intent(CaptureServiceActions.ACTION_CAPTURE_REQUESTED).setPackage(packageName))
                }, OVERLAY_DISMISS_DELAY_MILLIS)
            }
        }
        val params = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(180)
        }
        floatingView = button
        layoutParams = params
        windowManager.addView(button, params)
    }

    private fun setVisible(visible: Boolean) {
        val view = floatingView ?: return
        val isAttached = view.parent != null
        when {
            visible && !isAttached -> layoutParams?.let { windowManager.addView(view, it) }
            !visible && isAttached -> windowManager.removeView(view)
        }
    }

    private fun removeFloatingView() {
        floatingView?.let { view ->
            if (view.parent != null) windowManager.removeView(view)
        }
        floatingView = null
        layoutParams = null
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("FootballScreenshotOcr 正在运行")
        .setContentText("悬浮入口可采集当前屏幕")
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "停止",
            PendingIntent.getService(
                this,
                1,
                Intent(this, FloatingCaptureService::class.java).setAction(CaptureServiceActions.ACTION_STOP_FLOATING),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "屏幕采集入口", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class DragTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) dragged = true
                    if (dragged) {
                        params.x = (startX - dx.toInt()).coerceAtLeast(0)
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        floatingView?.let { windowManager.updateViewLayout(it, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) view.performClick()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_capture"
        private const val NOTIFICATION_ID = 8701

        @Volatile
        private var instance: FloatingCaptureService? = null

        fun isRunning(): Boolean = instance != null

        fun isVisible(): Boolean = instance?.floatingView?.parent != null

        fun setVisible(visible: Boolean) {
            instance?.mainHandler?.post { instance?.setVisible(visible) }
        }

        fun start(context: Context) {
            val intent = Intent(context, FloatingCaptureService::class.java).setAction(CaptureServiceActions.ACTION_START_FLOATING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FloatingCaptureService::class.java).setAction(CaptureServiceActions.ACTION_STOP_FLOATING))
        }

        private const val OVERLAY_DISMISS_DELAY_MILLIS = 300L
    }
}

class ProjectionCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var pendingCallback: ((CaptureResult) -> Unit)? = null
    private var requestTimeout: Runnable? = null
    private var armedTimeout: Runnable? = null
    private var releasingProjection = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CaptureServiceActions.ACTION_START_PROJECTION -> startProjection(intent)
            CaptureServiceActions.ACTION_STOP_PROJECTION -> {
                releaseProjection(
                    stopProjection = true,
                    failure = CaptureFailure(
                        CaptureFailureReason.PROJECTION_STOPPED,
                        "屏幕投影已停止，请重新授权后再试。",
                    ),
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseProjection(
            stopProjection = true,
            failure = if (pendingCallback != null) {
                CaptureFailure(CaptureFailureReason.PROJECTION_STOPPED, "屏幕投影会话已结束，请重新授权后再试。")
            } else {
                null
            },
        )
        super.onDestroy()
    }

    private fun startProjection(intent: Intent) {
        if (mediaProjection != null) return
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("屏幕投影已启用")
            .setContentText("切回盘口页面后，点击 FootballScreenshotOcr 悬浮入口采集一次")
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止投影",
                PendingIntent.getService(
                    this,
                    2,
                    Intent(this, ProjectionCaptureService::class.java).setAction(CaptureServiceActions.ACTION_STOP_PROJECTION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val resultCode = intent.getIntExtra(CaptureServiceActions.EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra<Intent>(CaptureServiceActions.EXTRA_RESULT_DATA)
        if (resultData == null || resultCode != android.app.Activity.RESULT_OK) {
            notifyProjectionState(armed = false, message = "屏幕投影授权未完成，请重新请求授权。")
            stopSelf()
            return
        }
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("系统未创建屏幕投影会话")
            mediaProjection = projection
            instance = this
            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseProjection(
                            stopProjection = false,
                            failure = CaptureFailure(CaptureFailureReason.PROJECTION_STOPPED, "屏幕投影已被系统或用户停止。"),
                        )
                        stopSelf()
                    }
                },
                handler,
            )
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader = reader
            reader.setOnImageAvailableListener(::onImageAvailable, handler)
            virtualDisplay = projection.createVirtualDisplay(
                "FootballScreenshotOcr-P1",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
            armedTimeout = Runnable {
                releaseProjection(
                    stopProjection = true,
                    failure = CaptureFailure(CaptureFailureReason.PROJECTION_STOPPED, "屏幕投影会话已超时，请重新授权后再试。"),
                )
                stopSelf()
            }.also { handler.postDelayed(it, MAX_ARMED_MILLIS) }
            notifyProjectionState(armed = true)
        } catch (_: SecurityException) {
            notifyProjectionState(armed = false, message = "屏幕投影权限不可用，请重新授权。")
            stopSelf()
        } catch (exception: RuntimeException) {
            notifyProjectionState(armed = false, message = exception.message ?: "屏幕投影启动失败，请重新授权后重试。")
            stopSelf()
        }
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: IllegalStateException) {
            null
        } ?: return
        val callback = pendingCallback
        if (callback == null) {
            image.close()
            return
        }
        pendingCallback = null
        requestTimeout?.let(handler::removeCallbacks)
        requestTimeout = null
        val result = try {
            val bitmap = image.toBitmap()
            if (CaptureRules.isVisuallyBlank(bitmap)) {
                bitmap.recycle()
                CaptureResult.Failure(
                    CaptureFailure(CaptureFailureReason.CONTENT_UNAVAILABLE, "屏幕内容不可用，请导入截图或重试。"),
                )
            } else {
                CaptureResult.Success(
                    CapturedFrame(bitmap, CaptureSource.MEDIA_PROJECTION, Instant.now(), bitmap.width, bitmap.height),
                )
            }
        } catch (_: RuntimeException) {
            CaptureResult.Failure(
                CaptureFailure(CaptureFailureReason.CONTENT_UNAVAILABLE, "屏幕内容不可用，请导入截图或重试。"),
            )
        } finally {
            image.close()
        }
        try {
            callback(result)
        } finally {
            stopSelf()
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.firstOrNull() ?: throw IllegalStateException("No image plane")
        val paddedWidth = plane.rowStride / plane.pixelStride
        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        val buffer: ByteBuffer = plane.buffer
        paddedBitmap.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return paddedBitmap
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        paddedBitmap.recycle()
        return cropped
    }

    private fun releaseProjection(stopProjection: Boolean, failure: CaptureFailure? = null) {
        if (releasingProjection) return
        if (mediaProjection == null && virtualDisplay == null && imageReader == null && pendingCallback == null) {
            if (failure == null) return
            notifyProjectionState(armed = false, message = failure.message, failureReason = failure.reason)
            return
        }
        releasingProjection = true
        try {
            failure?.let(::cancelPending)
            armedTimeout?.let(handler::removeCallbacks)
            armedTimeout = null
            requestTimeout?.let(handler::removeCallbacks)
            requestTimeout = null
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
            virtualDisplay?.release()
            virtualDisplay = null
            val projection = mediaProjection
            mediaProjection = null
            if (instance === this) instance = null
            if (stopProjection) projection?.stop()
            notifyProjectionState(armed = false, message = failure?.message, failureReason = failure?.reason)
        } finally {
            releasingProjection = false
        }
    }

    private fun cancelPending(failure: CaptureFailure) {
        val callback = pendingCallback
        pendingCallback = null
        requestTimeout?.let(handler::removeCallbacks)
        requestTimeout = null
        callback?.invoke(CaptureResult.Failure(failure))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "屏幕投影", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notifyProjectionState(
        armed: Boolean,
        message: String? = null,
        failureReason: CaptureFailureReason? = null,
    ) {
        sendBroadcast(
            Intent(CaptureServiceActions.ACTION_PROJECTION_STATE)
                .setPackage(packageName)
                .putExtra(CaptureServiceActions.EXTRA_PROJECTION_ARMED, armed)
                .putExtra(CaptureServiceActions.EXTRA_FAILURE_MESSAGE, message)
                .putExtra(CaptureServiceActions.EXTRA_FAILURE_REASON, failureReason?.name),
        )
    }

    companion object {
        private const val CHANNEL_ID = "screen_projection"
        private const val NOTIFICATION_ID = 8702
        private const val MAX_ARMED_MILLIS = 120_000L

        @Volatile
        private var instance: ProjectionCaptureService? = null

        fun isArmed(): Boolean = instance?.mediaProjection != null

        fun captureFrame(callback: (CaptureResult) -> Unit): Boolean {
            val service = instance ?: return false
            if (service.pendingCallback != null || service.imageReader == null) return false
            service.pendingCallback = callback
            service.requestTimeout = Runnable {
                service.pendingCallback = null
                callback(
                    CaptureResult.Failure(
                        CaptureFailure(CaptureFailureReason.TIMEOUT, "屏幕帧等待超时，请检查投影状态后重试。"),
                    ),
                )
                service.stopSelf()
            }.also { service.handler.postDelayed(it, FRAME_TIMEOUT_MILLIS) }
            return true
        }

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ProjectionCaptureService::class.java)
                .setAction(CaptureServiceActions.ACTION_START_PROJECTION)
                .putExtra(CaptureServiceActions.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureServiceActions.EXTRA_RESULT_DATA, resultData)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProjectionCaptureService::class.java).setAction(CaptureServiceActions.ACTION_STOP_PROJECTION))
        }
    }
}

private const val FRAME_TIMEOUT_MILLIS = 2_500L
