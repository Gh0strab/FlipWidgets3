package com.flipcoverwidgets.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flipcoverwidgets.app.service.CoverScreenWidgetHostService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot completed - starting cover screen widget host service")
            CoverScreenWidgetHostService.start(context)
        }
    }
}
