package com.flipcoverwidgets.app

import android.app.Application
import com.flipcoverwidgets.app.data.AppDatabase
import com.flipcoverwidgets.app.widget.WidgetHostManager

class FlipCoverWidgetsApp : Application() {
    
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        WidgetHostManager.startListening(this)
    }

    companion object {
        lateinit var instance: FlipCoverWidgetsApp
            private set
    }
}
