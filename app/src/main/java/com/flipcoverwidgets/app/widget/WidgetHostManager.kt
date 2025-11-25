package com.flipcoverwidgets.app.widget

import android.appwidget.AppWidgetHost
import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton manager for a single AppWidgetHost used by WidgetMirrorService.
 *
 * IMPORTANT:
 *  - Do NOT call startListening() from Activities. Only call allocate/delete from UI code if you're
 *    only allocating IDs — but the Host must be started/stopped by the Service.
 */
object WidgetHostManager {
    private const val HOST_ID = 0xF1F1// unique host id
            private val hostRef: AtomicReference<AppWidgetHost?> = AtomicReference(null)

    /**
     * Lazily create the host but do not startListening().
     * The service should call startListening(context) to begin listening.
     */
    fun getHost(context: Context): AppWidgetHost {
        hostRef.get()?.let { return it }

        val host = AppWidgetHost(context.applicationContext, HOST_ID)
        if (!hostRef.compareAndSet(null, host)) {
            // another thread beat us
            host.appWidgetIds // no-op
            return hostRef.get()!!
        }
        return host
    }

    /**
     * Start listening for widget updates. Should be called by WidgetMirrorService.onCreate()
     */
    fun startListening(context: Context) {
        val host = getHost(context)
        try {
            host.startListening()
        } catch (t: Throwable) {
            // startListening can throw if already started; swallow safely
        }
    }

    /**
     * Stop listening. Called when service is destroyed.
     */
    fun stopListening() {
        hostRef.get()?.let { host ->
            try {
                host.stopListening()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    /**
     * Delete and cleanup the host entirely (for app shutdown).
     */
    fun destroyHost() {
        hostRef.getAndSet(null)?.let { host ->
            try {
                host.stopListening()
            } catch (_: Throwable) {}
        }
    }
}
