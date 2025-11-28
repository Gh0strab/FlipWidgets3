package com.flipcoverwidgets.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.R
import com.flipcoverwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WidgetUpdateService : Service() {

    companion object {
        private const val TAG = "WidgetUpdateService"
        private const val CHANNEL_ID = "widget_update_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 500L

        fun start(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WidgetUpdateService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isUpdating = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON - checking cover screen")
                    checkAndStartUpdates()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF - stopping updates")
                    stopUpdates()
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isUpdating && isCoverScreenActive()) {
                serviceScope.launch {
                    try {
                        val repository = (applicationContext as FlipCoverWidgetsApp)
                            .database.widgetConfigDao()
                        val configs = repository.getAllConfigs()

                        for (config in configs) {
                            try {
                                WidgetMirrorService.captureWidgetSnapshot(
                                    applicationContext, 
                                    config.appWidgetId
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to capture widget ${config.appWidgetId}")
                            }
                        }

                        WidgetUpdater.updateAllWidgets(applicationContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Update cycle failed: ${e.message}")
                    }
                }
                
                forceUpdateNativeWidgets()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }
    
    private fun forceUpdateNativeWidgets() {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(applicationContext)
        
        listOf(
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider1", 1),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider2", 2),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider3", 3),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider4", 4),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider5", 5),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider6", 6),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider7", 7),
            Pair("com.flipcoverwidgets.app.widget.FlipCoverWidgetProvider8", 8)
        ).forEach { (providerClass, slot) ->
            try {
                val componentName = android.content.ComponentName(applicationContext, Class.forName(providerClass))
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val updateIntent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, applicationContext, Class.forName(providerClass)).apply {
                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    applicationContext.sendBroadcast(updateIntent)
                    Log.d(TAG, "Sent update broadcast to Slot $slot")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not update Slot $slot: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        checkAndStartUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopUpdates()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun checkAndStartUpdates() {
        if (isCoverScreenActive()) {
            startUpdates()
        } else {
            stopUpdates()
        }
    }

    private fun startUpdates() {
        if (!isUpdating) {
            isUpdating = true
            handler.post(updateRunnable)
            Log.d(TAG, "Started real-time updates (${UPDATE_INTERVAL_MS}ms interval)")
        }
    }

    private fun stopUpdates() {
        if (isUpdating) {
            isUpdating = false
            handler.removeCallbacks(updateRunnable)
            Log.d(TAG, "Stopped updates")
        }
    }

    private fun isCoverScreenActive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            return false
        }

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        for (display in displays) {
            if (display.state == Display.STATE_ON && display.displayId != Display.DEFAULT_DISPLAY) {
                return true
            }
        }

        return try {
            val windowManager = Class.forName("com.samsung.android.view.SemWindowManager")
            val getInstance = windowManager.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val isFolded = windowManager.getMethod("isFolded")
            val folded = isFolded.invoke(instance) as? Boolean ?: false
            folded && powerManager.isInteractive
        } catch (e: Exception) {
            try {
                val foldingFeature = Class.forName("androidx.window.layout.FoldingFeature")
                false
            } catch (e2: Exception) {
                displays.size > 1 && displays.any { 
                    it.state == Display.STATE_ON && it.displayId != Display.DEFAULT_DISPLAY 
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Widget Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps cover screen widgets updated"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen Widgets")
            .setContentText("Keeping widgets updated")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
