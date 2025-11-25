package com.flipcoverwidgets.app.service

import android.app.Service
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.widget.WidgetHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WidgetMirrorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WidgetMirrorService"

        private val hostViews = mutableMapOf<Int, AppWidgetHostView>()
        private var windowManager: WindowManager? = null

        private fun getWindowManager(context: Context): WindowManager {
            if (windowManager == null) {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            return windowManager!!
        }

        fun bindWidget(
            context: Context,
            appWidgetId: Int,
            providerInfo: AppWidgetProviderInfo,
            widthDp: Int = 512,
            heightDp: Int = 512
        ): Boolean {
            return try {
                WidgetHostManager.startListening(context)
                val host = WidgetHostManager.getHost(context)
                val remoteContext = context.createPackageContext(providerInfo.provider.packageName, 0)
                val hostView = host.createView(remoteContext, appWidgetId, providerInfo)

                hostView.setAppWidget(appWidgetId, providerInfo)

                val density = context.resources.displayMetrics.density
                val minWidthDp = providerInfo.minWidth
                val minHeightDp = providerInfo.minHeight

                val actualWidthDp = widthDp.coerceAtLeast(minWidthDp).coerceAtLeast(1)
                val actualHeightDp = heightDp.coerceAtLeast(minHeightDp).coerceAtLeast(1)

                val actualWidthPx = (actualWidthDp * density + 0.5f).toInt()
                val actualHeightPx = (actualHeightDp * density + 0.5f).toInt()

                hostView.updateAppWidgetSize(
                    android.os.Bundle(),
                    actualWidthDp,
                    actualHeightDp,
                    actualWidthDp,
                    actualHeightDp
                )

                hostView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(actualWidthPx, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(actualHeightPx, android.view.View.MeasureSpec.EXACTLY)
                )
                hostView.layout(0, 0, hostView.measuredWidth, hostView.measuredHeight)

                val params = WindowManager.LayoutParams(
                    hostView.measuredWidth,
                    hostView.measuredHeight,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

                getWindowManager(context).addView(hostView, params)

                hostViews[appWidgetId] = hostView
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind widget: ${e.message}")
                false
            }
        }

        fun unbindWidget(context: Context, appWidgetId: Int) {
            hostViews.remove(appWidgetId)?.let {
                try {
                    getWindowManager(context).removeView(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove view from window manager: ${e.message}")
                }
            }
        }

        fun getWidgetSnapshot(context: Context, appWidgetId: Int): Bitmap? {
            val snapshotFile = getSnapshotFile(context, appWidgetId)
            if (snapshotFile.exists()) {
                return try {
                    android.graphics.BitmapFactory.decodeFile(snapshotFile.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load snapshot: ${e.message}")
                    null
                }
            }
            return null
        }

        suspend fun captureWidgetSnapshot(context: Context, appWidgetId: Int): Bitmap? {
            WidgetHostManager.startListening(context)

            var hostView = hostViews[appWidgetId]

            if (hostView == null) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

                if (providerInfo != null) {
                    val repo = (context.applicationContext as FlipCoverWidgetsApp).database.widgetConfigDao()
                    val config = repo.getConfigByWidgetId(appWidgetId)
                    val widthDp = config?.widgetWidth ?: providerInfo.minWidth
                    val heightDp = config?.widgetHeight ?: providerInfo.minHeight

                    withContext(Dispatchers.Main) {
                        bindWidget(context, appWidgetId, providerInfo, widthDp, heightDp)
                    }
                    hostView = hostViews[appWidgetId]
                } else {
                    Log.w(TAG, "Widget $appWidgetId not bound, checking for saved snapshot")
                    return getWidgetSnapshot(context, appWidgetId)
                }
            }

            if (hostView == null) {
                Log.e(TAG, "Could not create host view for widget $appWidgetId")
                return getWidgetSnapshot(context, appWidgetId)
            }

            return withContext(Dispatchers.IO) {
                try {
                    val bitmap = Bitmap.createBitmap(
                        hostView.width.coerceAtLeast(1),
                        hostView.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    withContext(Dispatchers.Main) {
                        hostView.draw(canvas)
                    }

                    val snapshotFile = getSnapshotFile(context, appWidgetId)
                    FileOutputStream(snapshotFile).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }

                    bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to capture snapshot: ${e.message}")
                    getWidgetSnapshot(context, appWidgetId)
                }
            }
        }

        private fun getSnapshotFile(context: Context, appWidgetId: Int): File {
            val dir = File(context.filesDir, "widget_snapshots")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "widget_$appWidgetId.png")
        }
    }
}
