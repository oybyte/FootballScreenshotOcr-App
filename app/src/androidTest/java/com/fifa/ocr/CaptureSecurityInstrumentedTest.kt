package com.fifa.ocr

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fifa.ocr.feature.capture.CaptureFailureReason
import com.fifa.ocr.feature.capture.CaptureRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureSecurityInstrumentedTest {
    @Test
    fun secureFixtureMarksWindowSecureAndMapsPlatformDiagnostic() {
        ActivityScenario.launch<SecureWindowActivity>(Intent()) .use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
        val failure = CaptureRules.mapAccessibilityError(2, secureWindowErrorCode = 2, noAccessErrorCode = 3)
        assertEquals(CaptureFailureReason.SECURE_WINDOW, failure.reason)
        assertEquals("CAPTURE_BLOCKED", failure.diagnosticCode)
    }
}
