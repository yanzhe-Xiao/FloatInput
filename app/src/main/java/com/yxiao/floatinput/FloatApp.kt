package com.yxiao.floatinput

import android.app.Application
import com.yxiao.floatinput.updater.UpdateManager
import com.yxiao.floatinput.util.AppCrashHandler

class FloatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCrashHandler.init(this)
        // Clean any old or already-installed update APKs on startup
        UpdateManager(this).autoCleanOldApks()
    }
}
