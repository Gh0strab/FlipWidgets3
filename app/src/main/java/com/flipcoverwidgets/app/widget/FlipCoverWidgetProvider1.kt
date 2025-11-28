package com.flipcoverwidgets.app.widget

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.flipcoverwidgets.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlipCoverWidgetProvider1 : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout_slot1)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_text, "Slot 1\n$time")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
