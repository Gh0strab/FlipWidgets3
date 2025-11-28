package com.flipcoverwidgets.app.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.R
import com.flipcoverwidgets.app.service.WidgetMirrorService
import com.flipcoverwidgets.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        slotNumber: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = (context.applicationContext as FlipCoverWidgetsApp).database.widgetConfigDao()
            val config = repository.getConfigForSlot(slotNumber)

            val layoutId = when (slotNumber) {
                1 -> R.layout.widget_layout_slot1
                2 -> R.layout.widget_layout_slot2
                3 -> R.layout.widget_layout_slot3
                4 -> R.layout.widget_layout_slot4
                5 -> R.layout.widget_layout_slot5
                6 -> R.layout.widget_layout_slot6
                7 -> R.layout.widget_layout_slot7
                8 -> R.layout.widget_layout_slot8
                else -> R.layout.widget_layout
            }

            val views = RemoteViews(context.packageName, layoutId)

            if (config != null) {
                val bitmap = WidgetMirrorService.getWidgetSnapshot(context, config.appWidgetId)

                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        views.setViewVisibility(R.id.widget_label, android.view.View.GONE)
                    } else {
                        views.setTextViewText(R.id.widget_label, config.widgetLabel)
                        views.setViewVisibility(R.id.widget_label, android.view.View.VISIBLE)
                    }

                    val pendingIntent = getWidgetLaunchIntent(context, config.providerPackage, slotNumber)

                    views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } else {
                withContext(Dispatchers.Main) {
                    views.setTextViewText(R.id.widget_label, "Tap to configure\nSlot $slotNumber")
                    views.setViewVisibility(R.id.widget_label, android.view.View.VISIBLE)

                    val intent = Intent(context, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        slotNumber,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_label, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val providers = listOf(
            FlipCoverWidgetProvider1::class.java,
            FlipCoverWidgetProvider2::class.java,
            FlipCoverWidgetProvider3::class.java,
            FlipCoverWidgetProvider4::class.java,
            FlipCoverWidgetProvider5::class.java,
            FlipCoverWidgetProvider6::class.java,
            FlipCoverWidgetProvider7::class.java,
            FlipCoverWidgetProvider8::class.java
        )

        val slotNumbers = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        
        providers.forEachIndexed { index, provider ->
            val slotNumber = slotNumbers[index]
            val componentName = ComponentName(context, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, slotNumber)
            }
        }
    }

    private fun getWidgetLaunchIntent(context: Context, packageName: String, slotNumber: Int): PendingIntent {
        val packageManager = context.packageManager
        
        val launchIntent = try {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get launch intent for package $packageName: ${e.message}")
            null
        }

        val intent = launchIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        return PendingIntent.getActivity(
            context,
            slotNumber + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ActivityOptions.makeBasic().apply {
                launchDisplayId = 1
            }.toBundle()
        )
    }
}
