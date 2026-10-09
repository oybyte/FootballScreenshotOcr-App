package com.fifa.ocr

import android.app.Application
import com.fifa.ocr.data.storage.StorageRuntime

class FootballScreenshotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StorageRuntime.initialize(this)
    }
}
