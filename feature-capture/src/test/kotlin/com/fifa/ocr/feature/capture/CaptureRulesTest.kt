package com.fifa.ocr.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureRulesTest {
    @Test
    fun secureWindowUsesExistingContractDiagnosticAndNeverFallsBack() {
        val failure = CaptureRules.mapAccessibilityError(
            errorCode = 2,
            secureWindowErrorCode = 2,
            noAccessErrorCode = 3,
        )

        assertEquals(CaptureFailureReason.SECURE_WINDOW, failure.reason)
        assertEquals("CAPTURE_BLOCKED_BY_WINDOW_SECURITY", failure.message)
        assertEquals("CAPTURE_BLOCKED", failure.diagnosticCode)
        assertEquals(CaptureRoute.REPORT_FAILURE, CaptureRules.routeAfterAccessibilityFailure(failure, projectionArmed = true))
    }

    @Test
    fun ordinaryAccessibilityFailureUsesArmedProjectionOrRequestsForegroundConsent() {
        val failure = CaptureRules.mapAccessibilityError(90, secureWindowErrorCode = 2, noAccessErrorCode = 3)
        assertEquals(CaptureRoute.ACCESSIBILITY_THEN_PROJECTION, CaptureRules.routeAfterAccessibilityFailure(failure, true))
        assertEquals(CaptureRoute.REQUEST_PROJECTION_CONSENT, CaptureRules.routeAfterAccessibilityFailure(failure, false))
    }

    @Test
    fun missingAccessibilityPermissionDoesNotPretendProjectionCanReplaceIt() {
        val failure = CaptureRules.mapAccessibilityError(3, secureWindowErrorCode = 2, noAccessErrorCode = 3)
        assertEquals(CaptureFailureReason.PERMISSION_DENIED, failure.reason)
        assertEquals(CaptureRoute.REPORT_FAILURE, CaptureRules.routeAfterAccessibilityFailure(failure, projectionArmed = true))
    }

    @Test
    fun sharedImageUriAcceptsSingleImageAndClipDataFallback() {
        assertEquals(
            "content://picker/image/1",
            CaptureRules.sharedImageUri(
                "android.intent.action.SEND",
                "image/png",
                "content://picker/image/1",
                emptyList(),
            ),
        )
        assertEquals(
            "content://picker/image/2",
            CaptureRules.sharedImageUri(
                "android.intent.action.SEND",
                "image/jpeg",
                null,
                listOf("", "content://picker/image/2"),
            ),
        )
        assertEquals(
            "content://picker/image/3",
            CaptureRules.sharedImageUri(
                "android.intent.action.SEND_MULTIPLE",
                "image/webp",
                null,
                listOf("content://picker/image/3"),
            ),
        )
    }

    @Test
    fun sharedImageUriRejectsWrongActionAndNonImageMime() {
        assertNull(CaptureRules.sharedImageUri("android.intent.action.VIEW", "image/png", "content://x", emptyList()))
        assertNull(CaptureRules.sharedImageUri("android.intent.action.SEND", "text/plain", "content://x", emptyList()))
        assertNull(CaptureRules.sharedImageUri("android.intent.action.SEND", null, "content://x", emptyList()))
    }

    @Test
    fun decodeSamplingIsBoundedAndRejectsInvalidOrOversizedDimensions() {
        assertEquals(1, CaptureRules.decodeSampleSize(1080, 2400))
        assertEquals(2, CaptureRules.decodeSampleSize(6000, 4000))
        assertEquals(8, CaptureRules.decodeSampleSize(16000, 12000))
        assertNull(CaptureRules.decodeSampleSize(0, 500))
        assertNull(CaptureRules.decodeSampleSize(24_001, 100))
        assertNull(CaptureRules.decodeSampleSize(20_000, 10_000))
    }
}
