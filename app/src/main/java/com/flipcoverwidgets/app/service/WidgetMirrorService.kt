package com.flipcoverwidgets.app.service

import android.app.Service
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.IBinder
import android.util.Log
import android.view.View
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.widget.WidgetHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WidgetMirrorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WidgetMirrorService"
        
        private val cachedHostViews = mutableMapOf<Int, AppWidgetHostView>()

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
        
        fun clearCachedView(appWidgetId: Int) {
            cachedHostViews.remove(appWidgetId)
        }

        suspend fun captureWidgetSnapshot(context: Context, appWidgetId: Int): Bitmap? {
            WidgetHostManager.startListening(context)
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

            if (providerInfo == null) {
                Log.w(TAG, "Widget $appWidgetId has no provider info, checking for saved snapshot")
                return getWidgetSnapshot(context, appWidgetId)
            }

            val repo = (context.applicationContext as FlipCoverWidgetsApp).database.widgetConfigDao()
            val config = repo.getConfigByWidgetId(appWidgetId)
            val widthDp = config?.widgetWidth ?: providerInfo.minWidth
            val heightDp = config?.widgetHeight ?: providerInfo.minHeight

            return withContext(Dispatchers.Main) {
                try {
                    triggerWidgetUpdate(context, appWidgetId, providerInfo)
                    
                    val host = WidgetHostManager.getHost(context)
                    
                    val hostView = cachedHostViews.getOrPut(appWidgetId) {
                        host.createView(context.applicationContext, appWidgetId, providerInfo).also {
                            it.setAppWidget(appWidgetId, providerInfo)
                        }
                    }

                    val density = context.resources.displayMetrics.density
                    val actualWidthDp = widthDp.coerceAtLeast(1)
                    val actualHeightDp = heightDp.coerceAtLeast(1)
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
                        View.MeasureSpec.makeMeasureSpec(actualWidthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(actualHeightPx, View.MeasureSpec.EXACTLY)
                    )
                    hostView.layout(0, 0, hostView.measuredWidth, hostView.measuredHeight)
                    
                    hostView.invalidate()
                    hostView.requestLayout()

                    delay(100)

                    val width = hostView.measuredWidth.coerceAtLeast(1)
                    val height = hostView.measuredHeight.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    hostView.draw(canvas)

                    withContext(Dispatchers.IO) {
                        val snapshotFile = getSnapshotFile(context, appWidgetId)
                        FileOutputStream(snapshotFile).use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }

                    bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to capture snapshot: ${e.message}", e)
                    cachedHostViews.remove(appWidgetId)
                    getWidgetSnapshot(context, appWidgetId)
                }
            }
        }
        
        private fun triggerWidgetUpdate(context: Context, appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, android.R.id.list)
            } catch (e: Exception) {
                Log.d(TAG, "Widget data changed notification skipped (expected for some widgets)")
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
