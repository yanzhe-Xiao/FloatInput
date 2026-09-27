package com.yxiao.floatinput.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yxiao.floatinput.MainActivity
import com.yxiao.floatinput.R
import com.yxiao.floatinput.overlay.FloatingOverlayManager
import com.yxiao.floatinput.util.AppCrashHandler

class FloatWindowService : Service() {

    private val TAG = "FloatWindowService"
    private var overlayManager: FloatingOverlayManager? = null

    companion object {
        const val ACTION_START = "com.yxiao.floatinput.action.START"
        const val ACTION_STOP = "com.yxiao.floatinput.action.STOP"
        const val ACTION_TOGGLE = "com.yxiao.floatinput.action.TOGGLE"

        const val CHANNEL_ID = "channel_float_input_service"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatWindowService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("FloatWindowService", "startService failed", e)
                AppCrashHandler.saveCrash(context, e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatWindowService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("FloatWindowService", "stopService failed", e)
            }
        }

        fun toggle(context: Context) {
            val intent = Intent(context, FloatWindowService::class.java).apply {
                action = ACTION_TOGGLE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("FloatWindowService", "toggleService failed", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            isServiceRunning = true
            createNotificationChannel()
            startInForeground()

            overlayManager = FloatingOverlayManager(this).apply {
                onCloseRequested = {
                    stopSelf()
                }
                show()
            }

            FloatQuickSettingsTileService.requestTileUpdate(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Fatal error during onCreate of FloatWindowService", e)
            AppCrashHandler.saveCrash(this, e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_TOGGLE -> {
                    overlayManager?.toggleExpandCollapse()
                }
                ACTION_START -> {
                    overlayManager?.show()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onStartCommand", e)
            AppCrashHandler.saveCrash(this, e)
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            overlayManager?.onConfigurationChanged()
        } catch (e: Exception) {
            Log.e(TAG, "onConfigurationChanged error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            overlayManager?.hide()
            overlayManager = null
            FloatQuickSettingsTileService.requestTileUpdate(this)
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "createNotificationChannel error", e)
            }
        }
    }

    private fun startInForeground() {
        try {
            val mainIntent = Intent(this, MainActivity::class.java)
            val pMainIntent = PendingIntent.getActivity(
                this,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val toggleIntent = Intent(this, FloatWindowService::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pToggleIntent = PendingIntent.getService(
                this,
                1,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(this, FloatWindowService::class.java).apply {
                action = ACTION_STOP
            }
            val pStopIntent = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pMainIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_collapse, getString(R.string.notification_action_toggle), pToggleIntent)
                .addAction(R.drawable.ic_close, getString(R.string.notification_action_exit), pStopIntent)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+ (API 34/35)
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "startForeground with specific type failed, falling back to standard startForeground", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Throwable) {
                    Log.e(TAG, "Standard startForeground also failed", e2)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startInForeground overall error", e)
        }
    }
}
