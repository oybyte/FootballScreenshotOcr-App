package com.fifa.ocr

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class SecureWindowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(TextView(this).apply { text = "secure-fixture" })
    }
}
