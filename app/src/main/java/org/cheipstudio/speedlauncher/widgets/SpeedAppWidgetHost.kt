package org.cheipstudio.speedlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * v320: AppWidgetHost minimale.
 * - startListening cattura TransactionTooLargeException (issue 14255011 di AOSP)
 * - onCreateView ritorna view standard (no custom)
 */
class SpeedAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    
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
