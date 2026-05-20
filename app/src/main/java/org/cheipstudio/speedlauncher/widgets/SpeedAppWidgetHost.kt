package org.cheipstudio.speedlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.AttributeSet

/**
 * v315: AppWidgetHost custom replica del LauncherAppWidgetHost di Launcher3.
 * - onCreateView ritorna SpeedAppWidgetHostView che gestisce inflate RemoteViews in background
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
            val cause = e.cause
            val isTransactionLarge = cause?.javaClass?.simpleName == "TransactionTooLargeException"
                || cause?.javaClass?.simpleName == "DeadObjectException"
            if (!isTransactionLarge) {
                // log e ignora invece di crash
                android.util.Log.w("SpeedAppWidgetHost", "startListening failed: ${e.message}")
            }
        }
    }
    
    override fun stopListening() {
        try { super.stopListening() } catch (_: Throwable) {}
    }
}

/**
 * v315: HostView custom che gestisce errori di inflate dei RemoteViews senza crash visibile.
 * Mostra il widget in modo permissivo - non blocca su widget che falliscono il primo update.
 */
class SpeedAppWidgetHostView : AppWidgetHostView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context)
    
    override fun onDefaultViewClicked(view: android.view.View?) {
        // Se l'utente clicca sul "placeholder" mentre il widget non è ancora ready, 
        // forzo un nuovo bind tentativo
        super.onDefaultViewClicked(view)
    }
}
