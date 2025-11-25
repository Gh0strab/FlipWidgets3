package com.flipcoverwidgets.app.service

import android.app.Service
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.IBinder
import android.util.Log
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.widget.WidgetHostManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class WidgetMirrorService : Service() {

    private val TAG = "WidgetMirrorService"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var appWidgetManager: AppWidgetManager

    override fun onCreate() {
        super.onCreate()
        appWidgetManager = AppWidgetManager.getInstance(this)
        WidgetHostManager.startListening(this)
        Log.d(TAG, "MirrorService created — host Listening")
    }

    override fun onDestroy() {
        scope.cancel()
        WidgetHostManager.stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Capture widget bitmap without EVER attaching it to a window.
     */
    suspend fun captureWidgetSnapshot(appWidgetId: Int): Bitmap? =
        withContext(Dispatchers.Main) {

            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                ?: return@withContext null

            val host = WidgetHostManager.getHost(this@WidgetMirrorService)

            val remoteContext = try {
                createPackageContext(providerInfo.provider.packageName, 0)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }

            // Create host view OFFSCREEN
            val hostView = try {
                host.createView(remoteContext, appWidgetId, providerInfo)
            } catch (e: Exception) {
                Log.e(TAG, "createView failed: ${e.message}")
                return@withContext null
            }

            try {
                val minW = providerInfo.minWidth.coerceAtLeast(1)
                val minH = providerInfo.minHeight.coerceAtLeast(1)

                hostView.setAppWidget(appWidgetId, providerInfo)

                val widthSpec =
                    android.view.View.MeasureSpec.makeMeasureSpec(minW, android.view.View.MeasureSpec.EXACTLY)
                val heightSpec =
                    android.view.View.MeasureSpec.makeMeasureSpec(minH, android.view.View.MeasureSpec.EXACTLY)

                hostView.measure(widthSpec, heightSpec)
                hostView.layout(0, 0, minW, minH)

                val bitmap = Bitmap.createBitmap(minW, minH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                hostView.draw(canvas)

                saveSnapshot(appWidgetId, bitmap)
                bitmap

            } catch (e: Exception) {
                Log.e(TAG, "Snapshot error: ${e.message}")
                loadSnapshot(appWidgetId)
            }
        }

    /**
     * PUBLIC API used by your MainActivity -> calls snapshot + saves
     */
    fun captureAndPublish(appWidgetId: Int, slot: Int) {
        scope.launch {
            val bmp = captureWidgetSnapshot(appWidgetId)
            if (bmp != null) {
                Log.d(TAG, "Captured widget $appWidgetId for slot $slot (${bmp.width}x${bmp.height})")

                // save to DB or trigger your updater
                // WidgetUpdater.updateCoverWidget(...)
            }
        }
    }

    private fun snapshotFile(appWidgetId: Int): File {
        val dir = File(filesDir, "widget_snapshots")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "widget_$appWidgetId.png")
    }

    private fun saveSnapshot(appWidgetId: Int, bmp: Bitmap) {
        val f = snapshotFile(appWidgetId)
        FileOutputStream(f).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun loadSnapshot(appWidgetId: Int): Bitmap? {
        val f = snapshotFile(appWidgetId)
        return if (f.exists())
            android.graphics.BitmapFactory.decodeFile(f.absolutePath)
        else null
    }
}
