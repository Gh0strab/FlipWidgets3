package com.flipcoverwidgets.app.widget

import android.appwidget.AppWidgetHost
import android.content.Context

object WidgetHostManager {
    private const val HOST_ID = 2048
    private var appWidgetHost: AppWidgetHost? = null
    
    fun getHost(context: Context): AppWidgetHost {
        return appWidgetHost ?: synchronized(this) {
            appWidgetHost ?: AppWidgetHost(context.applicationContext, HOST_ID).also {
                appWidgetHost = it
            }
        }
    }
    
    fun startListening(context: Context) {
        getHost(context).startListening()
    }
    
    fun stopListening() {
        appWidgetHost?.stopListening()
    }
}
