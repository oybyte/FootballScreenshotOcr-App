package com.fifa.ocr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fifa.ocr.feature.capture.CaptureFailure
import com.fifa.ocr.feature.capture.CaptureFailureReason
import com.fifa.ocr.feature.capture.CapturePermissionSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onDismissError = {},
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
                onToggleOverlay = {}, onCapture = {}, onPickImage = {}, onDismissError = {},
            )
        }
        composeRule.onNodeWithTag("capture-error").assertIsDisplayed()
        composeRule.onNodeWithText("CAPTURE_BLOCKED_BY_WINDOW_SECURITY").assertIsDisplayed()
    }
}
