package com.yxiao.floatinput

import android.app.Application
import com.yxiao.floatinput.util.AppCrashHandler

class FloatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCrashHandler.init(this)
    }
}
