package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v172: Widget Speed Time — ora, data, batteria. Tap su ciascun blocco apre l'app correlata.
 */
class SpeedTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Re-aggiorna ogni minuto via alarm + notifica al cambiamento di tempo/batteria
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedTimeWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }
        
        private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_time)
            
            // Time
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            views.setTextViewText(R.id.timeText, timeFormat.format(Date()))
            
            // Date
            val dayFormat = SimpleDateFormat("d", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            views.setTextViewText(R.id.dateText, dayFormat.format(Date()))
            views.setTextViewText(R.id.dateMonth, monthFormat.format(Date()).uppercase(Locale.getDefault()))
            
            // Battery
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            views.setTextViewText(R.id.batteryText, if (pct >= 0) "$pct%" else "--%")
            
            // Click handlers
            // Tap ora → apri orologio (alarm clock)
            val timeIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(
                R.id.timeBlock,
                PendingIntent.getActivity(
                    context, 0, timeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            
            // Tap data → apri calendario  
            val calUri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(System.currentTimeMillis().toString())
                .build()
            val dateIntent = Intent(Intent.ACTION_VIEW, calUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            views.setOnClickPendingIntent(
                R.id.dateBlock,
                PendingIntent.getActivity(
                    context, 1, dateIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            
            // Tap batteria → impostazioni batteria
            val battIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Fallback: settings generici se ACTION_POWER_USAGE_SUMMARY non gestito
            val battResolved = battIntent.resolveActivity(context.packageManager)
            val finalBattIntent = if (battResolved != null) battIntent
                else Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            views.setOnClickPendingIntent(
                R.id.batteryBlock,
                PendingIntent.getActivity(
                    context, 2, finalBattIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Aggiorna su tick di tempo, batteria, fuso orario
        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_DATE_CHANGED -> updateAll(context)
        }
    }
}
