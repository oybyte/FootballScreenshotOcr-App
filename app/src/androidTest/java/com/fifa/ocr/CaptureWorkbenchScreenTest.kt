package com.fifa.ocr

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fifa.ocr.feature.capture.CaptureFailure
import com.fifa.ocr.feature.capture.CaptureFailureReason
import com.fifa.ocr.feature.capture.CapturePermissionSnapshot
import com.fifa.ocr.feature.capture.CaptureSource
import com.fifa.ocr.feature.capture.CapturedFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CaptureWorkbenchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingPermissionsExposeSettingsAndDisabledStatus() {
        composeRule.setContent {
            CaptureWorkbenchScreen(
                state = CaptureWorkbenchUiState(),
                onAccessibilitySettings = {}, onOverlaySettings = {}, onProjection = {},
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onSecureWindow = {}, onDismissError = {},
            )
        }
        composeRule.onNodeWithText("无障碍设置").assertIsDisplayed()
        composeRule.onNodeWithTag("accessibility-status").assertIsDisplayed()
        composeRule.onNodeWithText("未启用").assertIsDisplayed()
    }

    @Test
    fun captureFailureIsVisibleAndCanBeDismissed() {
        composeRule.setContent {
            CaptureWorkbenchScreen(
                state = CaptureWorkbenchUiState(
                    permissions = CapturePermissionSnapshot(true, true, true, false),
                    error = CaptureFailure(CaptureFailureReason.SECURE_WINDOW, "CAPTURE_BLOCKED_BY_WINDOW_SECURITY", "CAPTURE_BLOCKED"),
                ),
                onAccessibilitySettings = {}, onOverlaySettings = {}, onProjection = {},
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onSecureWindow = {}, onDismissError = {},
            )
        }
        composeRule.onNodeWithTag("capture-error").assertIsDisplayed()
        composeRule.onNodeWithText("CAPTURE_BLOCKED_BY_WINDOW_SECURITY").assertIsDisplayed()
    }

    @Test
    fun projectionStatusAndSecureWindowEntryAreVisible() {
        composeRule.setContent {
            CaptureWorkbenchScreen(
                state = CaptureWorkbenchUiState(
                    permissions = CapturePermissionSnapshot(true, true, true, true),
                ),
                onAccessibilitySettings = {}, onOverlaySettings = {}, onProjection = {},
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onSecureWindow = {}, onDismissError = {},
            )
        }
        composeRule.onNodeWithTag("projection-status").assertIsDisplayed()
        composeRule.onNodeWithText("本次已授权").assertIsDisplayed()
        composeRule.onNodeWithTag("open-secure-window").assertIsDisplayed()
    }

    @Test
    fun successfulPreviewIsVisible() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            CaptureWorkbenchScreen(
                state = CaptureWorkbenchUiState(
                    frame = CapturedFrame(bitmap, CaptureSource.MANUAL_IMPORT, Instant.EPOCH, 8, 8),
                ),
                onAccessibilitySettings = {}, onOverlaySettings = {}, onProjection = {},
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onSecureWindow = {}, onDismissError = {},
            )
        }
        composeRule.onNodeWithTag("capture-preview").assertIsDisplayed()
        composeRule.onNodeWithText("8 × 8 · manual_import").assertIsDisplayed()
        bitmap.recycle()
    }
}
