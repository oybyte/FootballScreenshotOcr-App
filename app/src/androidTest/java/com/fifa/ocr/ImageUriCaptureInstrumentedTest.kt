package com.fifa.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fifa.ocr.feature.capture.CaptureFailureReason
import com.fifa.ocr.feature.capture.CaptureResult
import com.fifa.ocr.feature.capture.CaptureSource
import com.fifa.ocr.feature.capture.ImageUriCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class ImageUriCaptureInstrumentedTest {
    @Test
    fun decodesFakeResolverImageAsManualImport() {
        val fixture = ByteArrayOutputStream().also { output ->
            val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
        }.toByteArray()
        val fixtureFile = File.createTempFile("p1-image", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        fixtureFile.writeBytes(fixture)
        val result = ImageUriCapture { FileInputStream(fixtureFile) }
            .decode(Uri.parse("content://fake/image"))
        fixtureFile.delete()
        assertTrue("decode result: $result", result is CaptureResult.Success)
        result as CaptureResult.Success
        assertEquals(CaptureSource.MANUAL_IMPORT, result.frame.source)
        assertEquals(32, result.frame.displayWidth)
        assertEquals(24, result.frame.displayHeight)
        result.frame.bitmap.recycle()
    }

    @Test
    fun unreadableUriReturnsDecodeFailure() {
        val result = ImageUriCapture { null }
            .decode(Uri.parse("content://com.fifa.ocr.missing/image"))

        assertTrue(result is CaptureResult.Failure)
        assertEquals(CaptureFailureReason.PERMISSION_DENIED, (result as CaptureResult.Failure).failure.reason)
    }

}
