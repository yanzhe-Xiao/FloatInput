package com.yxiao.floatinput

import android.app.Application
import com.yxiao.floatinput.updater.UpdateManager
import androidx.appcompat.app.AppCompatDelegate
import com.yxiao.floatinput.util.PreferencesHelper
import com.yxiao.floatinput.util.AppCrashHandler

class FloatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCrashHandler.init(this)
        val prefs = PreferencesHelper.getInstance(this)
        AppCompatDelegate.setDefaultNightMode(
            when (prefs.themeMode) {
                PreferencesHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                PreferencesHelper.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                else                          -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        // Clean any old or already-installed update APKs on startup
        UpdateManager(this).autoCleanOldApks()
    }
}
