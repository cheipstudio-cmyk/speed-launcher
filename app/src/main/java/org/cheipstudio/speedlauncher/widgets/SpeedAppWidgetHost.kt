package org.cheipstudio.speedlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews

/**
 * v315: AppWidgetHost custom replica del LauncherAppWidgetHost di Launcher3.
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
 * v316: HostView custom con error handling - cattura eccezioni RemoteViews per evitare
 * "Aggiunta widget non riuscita" placeholder. Replica pattern Launcher3.
 */
class SpeedAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    
    private var lastValidRemoteViews: RemoteViews? = null
    private var retryCount = 0
    
    override fun updateAppWidget(remoteViews: RemoteViews?) {
        try {
            super.updateAppWidget(remoteViews)
            // Reset retry count su success
            if (remoteViews != null) {
                lastValidRemoteViews = remoteViews
                retryCount = 0
            }
        } catch (e: Throwable) {
            // RemoteViews inflate fallisce - non mostrare error placeholder
            // se abbiamo una view valida precedente, la teniamo
            android.util.Log.w("SpeedAppWidgetHostView", 
                "updateAppWidget exception (will retry): ${e.message}")
            
            // Retry con i RemoteViews validi precedenti
            if (lastValidRemoteViews != null && retryCount == 0) {
                retryCount++
                try {
                    super.updateAppWidget(lastValidRemoteViews)
                } catch (_: Throwable) {}
            }
            // Altrimenti tieni lo stato attuale - il widget si aggiornerà dal prossimo broadcast
        }
    }
    
    override fun getDefaultView(): android.view.View {
        // Override per evitare il default view "Aggiunta widget non riuscita".
        // Restituisce una view trasparente vuota - lasciamo che il widget si carichi
        // quando arriva il prossimo update dal provider.
        try {
            val empty = android.view.View(context)
            empty.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return empty
        } catch (_: Throwable) {
            return super.getDefaultView()
        }
    }
}
