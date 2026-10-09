package com.fifa.ocr.core.contract

/** Shared P1/P2 image bounds. Keeping the limits here prevents storage and import from drifting. */
object CaptureImageLimits {
    const val MAX_EDGE = 4_096
    const val MAX_PIXELS = 8_000_000L
    const val MAX_SOURCE_EDGE = 24_000
    const val MAX_SOURCE_PIXELS = 199_000_000L
}
