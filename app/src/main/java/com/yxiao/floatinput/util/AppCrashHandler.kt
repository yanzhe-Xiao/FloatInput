package com.yxiao.floatinput.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            saveCrash(context, e)
        } catch (_: Exception) {}

        // Delegate to system default handler
        defaultHandler?.uncaughtException(t, e)
    }

    companion object {
        private const val TAG = "AppCrashHandler"
        private const val PREF_CRASH = "app_crash_prefs"
        private const val KEY_LAST_CRASH = "key_last_crash_trace"
        private const val KEY_LAST_CRASH_TIME = "key_last_crash_time"

        fun init(context: Context) {
            val handler = AppCrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun saveCrash(context: Context, e: Throwable) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            val stackTrace = sw.toString()
            Log.e(TAG, "Uncaught exception caught:\n$stackTrace")

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val fullInfo = "[$time]\n$stackTrace"

            try {
                val prefs = context.getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_LAST_CRASH, fullInfo)
                    .putString(KEY_LAST_CRASH_TIME, time)
                    .apply()

                val file = File(context.filesDir, "last_crash.txt")
                file.writeText(fullInfo)
            } catch (err: Exception) {
                Log.e(TAG, "Failed to write crash log to disk", err)
            }
        }

        fun getLastCrash(context: Context): String? {
            return try {
                val prefs = context.getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                prefs.getString(KEY_LAST_CRASH, null)
            } catch (_: Exception) {
                null
            }
        }

        fun clearCrash(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                val file = File(context.filesDir, "last_crash.txt")
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
    }
}
