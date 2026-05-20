package org.cheipstudio.speedlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * v315: AppWidgetHost custom replica del LauncherAppWidgetHost di Launcher3.
 * - onCreateView ritorna SpeedAppWidgetHostView (per future custom)
 * - startListening cattura TransactionTooLargeException (RemoteViews troppo grandi)
 */
class SpeedAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return SpeedAppWidgetHostView(context)
    }
    
    override fun startListening() {
        try {
            super.startListening()
        } catch (e: Exception) {
            // RemoteViews list troppo grande → ok continuare, il bind è già established
            val causeName = e.cause?.javaClass?.simpleName ?: ""
            if (causeName != "TransactionTooLargeException" && causeName != "DeadObjectException") {
                android.util.Log.w("SpeedAppWidgetHost", "startListening failed: ${e.message}")
            }
        }
    }
    
    override fun stopListening() {
        try { super.stopListening() } catch (_: Throwable) {}
    }
}

/**
 * v315: HostView custom. Per ora ritorna view default. Pronto per future override.
 */
class SpeedAppWidgetHostView(context: Context) : AppWidgetHostView(context)
