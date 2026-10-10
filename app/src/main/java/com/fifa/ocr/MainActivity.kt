package com.fifa.ocr

import android.app.Activity
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fifa.ocr.feature.capture.AccessibilityCaptureProvider
import com.fifa.ocr.feature.capture.AccessibilityCaptureService
import com.fifa.ocr.feature.capture.AccessibilityServiceRegistry
import com.fifa.ocr.feature.capture.CaptureFailure
import com.fifa.ocr.feature.capture.CaptureFailureReason
import com.fifa.ocr.feature.capture.CapturePermissionSnapshot
import com.fifa.ocr.feature.capture.CaptureProvider
import com.fifa.ocr.feature.capture.CaptureResult
import com.fifa.ocr.feature.capture.CaptureRoute
import com.fifa.ocr.feature.capture.CaptureRules
import com.fifa.ocr.feature.capture.CaptureServiceActions
import com.fifa.ocr.feature.capture.CapturedFrame
import com.fifa.ocr.feature.capture.FloatingCaptureService
import com.fifa.ocr.feature.capture.ImageUriCapture
import com.fifa.ocr.feature.capture.MediaProjectionCaptureProvider
import com.fifa.ocr.feature.capture.ProjectionCaptureService
import com.fifa.ocr.feature.capture.accessibilityReady
import com.fifa.ocr.ui.theme.FootballScreenshotOcrTheme
import java.util.concurrent.Executors

data class CaptureWorkbenchUiState(
    val permissions: CapturePermissionSnapshot? = null,
    val overlayRunning: Boolean? = null,
    val isCapturing: Boolean = false,
    val frame: CapturedFrame? = null,
    val error: CaptureFailure? = null,
    val status: String? = null,
)

fun CaptureWorkbenchUiState.withProjectionState(armed: Boolean): CaptureWorkbenchUiState =
    permissions?.let { copy(permissions = it.copy(projectionArmed = armed)) } ?: this

fun CaptureWorkbenchUiState.withFloatingState(running: Boolean): CaptureWorkbenchUiState =
    if (permissions == null) this else copy(overlayRunning = running)

class MainActivity : ComponentActivity() {
    private val accessibilityProvider: CaptureProvider = AccessibilityCaptureProvider()
    private val projectionProvider = MediaProjectionCaptureProvider()
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var uiState by mutableStateOf(CaptureWorkbenchUiState())
    private var receiverRegistered = false
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ProjectionCaptureService.start(this, result.resultCode, data)
            uiState = uiState.copy(status = "正在启动屏幕投影…", error = null)
        } else {
            uiState = uiState.copy(status = "已取消屏幕投影授权。")
        }
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::importImage) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CaptureServiceActions.ACTION_CAPTURE_REQUESTED -> beginCapture(true)
                CaptureServiceActions.ACTION_PROJECTION_STATE -> {
                    val armed = intent.getBooleanExtra(CaptureServiceActions.EXTRA_PROJECTION_ARMED, false)
                    val message = intent.getStringExtra(CaptureServiceActions.EXTRA_FAILURE_MESSAGE)
                    val failureReason = intent.getStringExtra(CaptureServiceActions.EXTRA_FAILURE_REASON)
                        ?.let { value -> runCatching { CaptureFailureReason.valueOf(value) }.getOrNull() }
                    val failure = message?.let { CaptureFailure(failureReason ?: CaptureFailureReason.SERVICE_UNAVAILABLE, it) }
                    uiState = uiState.withProjectionState(armed).copy(
                        status = if (armed) "屏幕投影已就绪，请切换到盘口页面后点击悬浮入口。" else message ?: uiState.status,
                        error = failure ?: uiState.error,
                    )
                }
                CaptureServiceActions.ACTION_FLOATING_STATE -> {
                    val running = intent.getBooleanExtra(CaptureServiceActions.EXTRA_FLOATING_RUNNING, false)
                    val message = intent.getStringExtra(CaptureServiceActions.EXTRA_FAILURE_MESSAGE)
                    uiState = uiState.withFloatingState(running).copy(
                        error = message?.let { CaptureFailure(CaptureFailureReason.SERVICE_UNAVAILABLE, it) },
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FootballScreenshotOcrTheme {
                CaptureWorkbenchScreen(uiState, ::openAccessibilitySettings, ::openOverlaySettings, ::requestProjection, ::toggleOverlay, { beginCapture(false) }, ::pickImage, ::openSecureWindow) { uiState = uiState.copy(error = null) }
            }
        }
        registerCaptureReceiver()
        handleIncomingImage(intent)
    }

    override fun onResume() { super.onResume(); refreshPermissions() }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIncomingImage(intent) }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        imageExecutor.shutdown()
        uiState.frame?.bitmap?.recycle()
        super.onDestroy()
    }

    private fun registerCaptureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(CaptureServiceActions.ACTION_CAPTURE_REQUESTED)
            addAction(CaptureServiceActions.ACTION_PROJECTION_STATE)
            addAction(CaptureServiceActions.ACTION_FLOATING_STATE)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun refreshPermissions() {
        val serviceName = ComponentName(this, AccessibilityCaptureService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any { it.equals(serviceName, true) }
        uiState = uiState.copy(
            permissions = CapturePermissionSnapshot(enabled, AccessibilityServiceRegistry.isConnected(), Settings.canDrawOverlays(this), projectionProvider.isArmed()),
            overlayRunning = FloatingCaptureService.isRunning(),
        )
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    private fun openOverlaySettings() = startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    private fun requestProjection() = projectionLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    private fun openSecureWindow() = startActivity(Intent(this, SecureWindowActivity::class.java))

    private fun toggleOverlay() {
        refreshPermissions()
        val permissions = uiState.permissions ?: run {
            uiState = uiState.copy(status = "正在查询权限状态，请稍后重试。")
            return
        }
        if (!permissions.overlayAllowed) return openOverlaySettings()
        if (uiState.overlayRunning == true) {
            FloatingCaptureService.stop(this)
            uiState = uiState.copy(overlayRunning = false, status = "悬浮入口已停止。")
        } else {
            try {
                FloatingCaptureService.start(this)
                uiState = uiState.copy(overlayRunning = true, status = "悬浮入口已启动。")
            } catch (_: RuntimeException) {
                uiState = uiState.copy(
                    overlayRunning = false,
                    error = CaptureFailure(CaptureFailureReason.SERVICE_UNAVAILABLE, "悬浮入口启动失败，请确认悬浮窗权限并重试。"),
                )
            }
        }
    }

    private fun beginCapture(restoreOverlay: Boolean) {
        if (uiState.permissions == null) {
            uiState = uiState.copy(status = "正在查询权限状态，请稍后重试。")
            return
        }
        uiState = uiState.copy(isCapturing = true, error = null, status = "正在采集当前屏幕…")
        accessibilityProvider.capture { result ->
            if (result is CaptureResult.Success) completeCapture(result, restoreOverlay)
            else {
                val failure = (result as CaptureResult.Failure).failure
                when (CaptureRules.routeAfterAccessibilityFailure(failure, projectionProvider.isArmed())) {
                    CaptureRoute.ACCESSIBILITY_THEN_PROJECTION -> projectionProvider.capture { completeCapture(it, restoreOverlay) }
                    CaptureRoute.REQUEST_PROJECTION_CONSENT -> {
                        completeCapture(result, restoreOverlay)
                        if (!restoreOverlay) requestProjection()
                    }
                    else -> completeCapture(result, restoreOverlay)
                }
            }
        }
    }

    private fun completeCapture(result: CaptureResult, restoreOverlay: Boolean) = runOnUiThread {
        try {
            when (result) {
                is CaptureResult.Success -> {
                    uiState.frame?.bitmap?.recycle()
                    uiState = uiState.copy(isCapturing = false, frame = result.frame, error = null, status = "采集完成：${result.frame.source.wireName} · ${result.frame.displayWidth} × ${result.frame.displayHeight}")
                }
                is CaptureResult.Failure -> uiState = uiState.copy(isCapturing = false, error = result.failure, status = "采集未完成，可重试或导入图片。")
            }
        } finally {
            if (restoreOverlay && Settings.canDrawOverlays(this) && FloatingCaptureService.isRunning()) FloatingCaptureService.setVisible(true)
            refreshPermissions()
        }
    }

    private fun pickImage() = imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    private fun handleIncomingImage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val clips = buildList<String> { intent.clipData?.let { data -> repeat(data.itemCount) { data.getItemAt(it).uri?.toString()?.let(::add) } } }
        val uri = CaptureRules.sharedImageUri(intent.action, intent.type, stream?.toString(), clips)?.let(Uri::parse)
        if (uri == null) uiState = uiState.copy(error = CaptureFailure(CaptureFailureReason.INVALID_IMAGE, "分享内容不是可读取的图片，请重新选择。")) else importImage(uri)
    }

    private fun importImage(uri: Uri) {
        uiState = uiState.copy(isCapturing = true, error = null, status = "正在读取图片…")
        imageExecutor.execute {
            val result = ImageUriCapture(contentResolver).decode(uri)
            runOnUiThread {
                when (result) {
                    is CaptureResult.Success -> { uiState.frame?.bitmap?.recycle(); uiState = uiState.copy(isCapturing = false, frame = result.frame, status = "图片已导入，仅保留在本次预览内存中。") }
                    is CaptureResult.Failure -> uiState = uiState.copy(isCapturing = false, error = result.failure, status = null)
                }
            }
        }
    }
}

@Composable
fun CaptureWorkbenchScreen(
    state: CaptureWorkbenchUiState,
    onAccessibilitySettings: () -> Unit,
    onOverlaySettings: () -> Unit,
    onProjection: () -> Unit,
    onToggleOverlay: () -> Unit,
    onCapture: () -> Unit,
    onPickImage: () -> Unit,
    onSecureWindow: () -> Unit,
    onDismissError: () -> Unit,
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("屏幕采集工作台", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text("P1 单屏验证 · 图片只在当前预览中保留", style = MaterialTheme.typography.bodyMedium)
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val permissions = state.permissions
                    StatusRow("无障碍服务", when {
                        permissions == null -> "正在查询"
                        permissions.accessibilityReady() -> "已连接"
                        permissions.accessibilityEnabled -> "已启用，连接中"
                        else -> "未启用"
                    }, "accessibility-status")
                    StatusRow("悬浮窗", when {
                        permissions == null -> "正在查询"
                        permissions.overlayAllowed -> "已授权"
                        else -> "未授权"
                    }, "overlay-status")
                    StatusRow("屏幕投影", when {
                        permissions == null -> "正在查询"
                        permissions.projectionArmed -> "本次已授权"
                        else -> "未授权"
                    }, "projection-status")
                    StatusRow("悬浮入口", when (state.overlayRunning) {
                        null -> "正在查询"
                        true -> "运行中"
                        false -> "已停止"
                    }, "floating-status")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAccessibilitySettings, Modifier.weight(1f)) { Text("无障碍设置") }
                OutlinedButton(onClick = onOverlaySettings, Modifier.weight(1f)) { Text("悬浮窗设置") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleOverlay, enabled = state.overlayRunning != null, modifier = Modifier.weight(1f).testTag("toggle-floating")) { Text(if (state.overlayRunning == true) "停止悬浮入口" else "启动悬浮入口") }
                OutlinedButton(onClick = onProjection, Modifier.weight(1f).testTag("request-projection")) { Text("请求投影授权") }
            }
            OutlinedButton(onClick = onSecureWindow, modifier = Modifier.fillMaxWidth().testTag("open-secure-window")) {
                Text("打开安全窗口测试")
            }
            TextButton(onClick = onCapture, enabled = !state.isCapturing && state.permissions != null, modifier = Modifier.testTag("capture-now")) { Text("采集当前屏幕") }
            OutlinedButton(onClick = onPickImage, enabled = !state.isCapturing, modifier = Modifier.testTag("pick-image")) { Text("选择图片") }
            state.status?.let { Text(it, Modifier.testTag("capture-status")) }
            state.error?.let { failure ->
                Surface(Modifier.fillMaxWidth().testTag("capture-error"), RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(12.dp)) { Text(failure.message); failure.diagnosticCode?.let { Text("诊断：$it") }; TextButton(onClick = onDismissError) { Text("关闭") } }
                }
            }
            if (state.isCapturing) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.frame?.let { frame ->
                Text("采集预览", fontWeight = FontWeight.Medium)
                Image(frame.bitmap.asImageBitmap(), "${frame.source.wireName} 屏幕采集预览", Modifier.fillMaxWidth().sizeIn(maxHeight = 520.dp).testTag("capture-preview"))
                Text("${frame.displayWidth} × ${frame.displayHeight} · ${frame.source.wireName}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, tag: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(label); Text(value, Modifier.testTag(tag)) }
}
