package com.fifa.ocr.feature.capture

import com.fifa.ocr.data.storage.PersistableFrame
import com.fifa.ocr.core.contract.CaptureProvider as ContractCaptureProvider

/** Converts the P1 Android capture result at the P2 persistence boundary. */
fun CapturedFrame.toPersistableFrame(): PersistableFrame = PersistableFrame(
    bitmap = bitmap,
    source = when (source) {
        CaptureSource.ACCESSIBILITY -> ContractCaptureProvider.ACCESSIBILITY
        CaptureSource.MEDIA_PROJECTION -> ContractCaptureProvider.MEDIA_PROJECTION
        CaptureSource.MANUAL_IMPORT -> ContractCaptureProvider.MANUAL_IMPORT
    },
    capturedAt = capturedAt,
    displayWidth = displayWidth,
    displayHeight = displayHeight,
)
