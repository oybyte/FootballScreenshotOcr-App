package com.fifa.ocr

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Internal fixture used by instrumentation to exercise FLAG_SECURE handling. */
class SecureWindowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(TextView(this).apply { text = "secure-fixture" })
    }
}
