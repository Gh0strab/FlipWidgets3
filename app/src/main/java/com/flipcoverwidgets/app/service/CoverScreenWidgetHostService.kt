package com.flipcoverwidgets.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CoverScreenWidgetHostService : Service() {

    companion object {
        private const val TAG = "CoverScreenWidgetHost"
        private const val COVER_SCREEN_DISPLAY_ID = 1
        private const val CHANNEL_ID = "cover_screen_widget_channel"
        private const val NOTIFICATION_ID = 1002
        
        fun start(context: Context) {
            val intent = Intent(context, CoverScreenWidgetHostService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoverScreenWidgetHostService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var appWidgetHost: AppWidgetHost? = null
    private var containerView: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var coverScreenDisplay: Display? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeCoverScreenWidgetHost()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cover Screen Widgets",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen Widgets")
            .setContentText("Displaying widgets on cover screen")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initializeCoverScreenWidgetHost() {
        try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            coverScreenDisplay = displayManager.getDisplay(COVER_SCREEN_DISPLAY_ID)

            if (coverScreenDisplay == null) {
                Log.w(TAG, "Cover screen display not available")
                return
            }

            Log.d(TAG, "Cover screen display found: ${coverScreenDisplay?.name}")

            appWidgetHost = AppWidgetHost(applicationContext, 1001).also {
                it.startListening()
            }

            // Create container view for widgets
            containerView = FrameLayout(applicationContext)
            
            // Create window on cover screen display
            createCoverScreenWindow()
            
            // Bind widgets after a short delay to ensure everything is initialized
            serviceScope.launch {
                kotlinx.coroutines.delay(500)
                bindWidgetsToHost()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize cover screen widget host: ${e.message}", e)
        }
    }

    private fun createCoverScreenWindow() {
        val container = containerView ?: return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = android.graphics.PixelFormat.RGBA_8888
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                
                // Target the cover screen display
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    displayId = COVER_SCREEN_DISPLAY_ID
                }
            }

            container.setBackgroundColor(android.graphics.Color.BLACK)
            windowManager?.addView(container, params)
            Log.d(TAG, "Cover screen window created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cover screen window: ${e.message}", e)
        }
    }

    private fun bindWidgetsToHost() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val host = appWidgetHost ?: return
            val container = containerView ?: return
            val repository = (applicationContext as FlipCoverWidgetsApp).database.widgetConfigDao()
            val configs = repository.getAllConfigs()

            Log.d(TAG, "Found ${configs.size} widget configs to bind")

            configs.forEach { config ->
                try {
                    val info = appWidgetManager.getAppWidgetInfo(config.appWidgetId)
                    if (info != null) {
                        val hostView = host.createView(
                            applicationContext,
                            config.appWidgetId,
                            info
                        )

                        hostView.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )

                        container.addView(hostView)
                        Log.d(TAG, "Widget ${config.appWidgetId} bound to cover screen")
                    } else {
                        Log.w(TAG, "Widget info null for ${config.appWidgetId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind widget ${config.appWidgetId}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind widgets: ${e.message}", e)
        }
    }

    private fun cleanup() {
        try {
            appWidgetHost?.stopListening()
            containerView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }
}
