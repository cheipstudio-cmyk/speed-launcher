package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.R

/**
 * v179: Widget Speed Time. TextClock auto-aggiorna ora/data.
 * Battery via onUpdate ogni 30 min (default), tap apre app.
 */
class SpeedTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidget(context, mgr, id) } catch (_: Throwable) {}
        }
    }

    companion object {
        fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_time)
            
            // Battery (TextClock gestisce ora/data automaticamente)
            val pct = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Throwable) { -1 }
            views.setTextViewText(R.id.batteryText, if (pct >= 0) "$pct%" else "--%")
            
            // Click Ora → orologio
            val timeIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(R.id.timeBlock, PendingIntent.getActivity(
                context, 1001, timeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            
            // Click Data → calendario
            val calUri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(System.currentTimeMillis().toString()).build()
            val dateIntent = Intent(Intent.ACTION_VIEW, calUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(R.id.dateBlock, PendingIntent.getActivity(
                context, 1002, dateIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            
            // Click Batteria → impostazioni batteria
            val battIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val finalIntent = if (battIntent.resolveActivity(context.packageManager) != null) battIntent
                else Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            views.setOnClickPendingIntent(R.id.batteryBlock, PendingIntent.getActivity(
                context, 1003, finalIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            
            mgr.updateAppWidget(id, views)
        }
    }
}
