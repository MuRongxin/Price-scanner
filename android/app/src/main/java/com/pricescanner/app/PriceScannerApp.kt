package com.pricescanner.app

import android.app.Application
import com.pricescanner.app.util.PinyinUtil

class PriceScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PinyinUtil.init(this)
    }
}
