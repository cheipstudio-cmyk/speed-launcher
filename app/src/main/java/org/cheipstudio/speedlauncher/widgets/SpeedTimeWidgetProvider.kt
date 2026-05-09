package org.cheipstudio.speedlauncher.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.R

/**
 * v178: Widget Speed Time. Time/date via TextClock (auto-refresh nativo).
 * Batteria via BatteryManager + alarm refresh ogni 60s.
 */
class SpeedTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
        scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelSchedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            updateAll(context)
            scheduleNextUpdate(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "org.cheipstudio.speedlauncher.WIDGET_TIME_TICK"
        private const val REQ_CODE = 8421
        private const val INTERVAL_MS = 60_000L  // ogni minuto
        
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedTimeWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }
        
        private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_time)
            
            // Battery (TextClock gestisce time + date automaticamente)
            val pct = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Throwable) { -1 }
            views.setTextViewText(R.id.batteryText, if (pct >= 0) "$pct%" else "--%")
            
            // Click intents
            try {
                val timeIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                views.setOnClickPendingIntent(R.id.timeBlock, PendingIntent.getActivity(
                    context, 0, timeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            
            try {
                val calUri = CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .appendPath(System.currentTimeMillis().toString()).build()
                val dateIntent = Intent(Intent.ACTION_VIEW, calUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                views.setOnClickPendingIntent(R.id.dateBlock, PendingIntent.getActivity(
                    context, 1, dateIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            
            try {
                val battIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val resolved = battIntent.resolveActivity(context.packageManager) != null
                val finalIntent = if (resolved) battIntent
                    else Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                views.setOnClickPendingIntent(R.id.batteryBlock, PendingIntent.getActivity(
                    context, 2, finalIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            
            try {
                mgr.updateAppWidget(id, views)
            } catch (_: Throwable) {}
        }
        
        private fun scheduleNextUpdate(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SpeedTimeWidgetProvider::class.java).apply {
                    action = ACTION_TICK
                }
                val pi = PendingIntent.getBroadcast(
                    context, REQ_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL_MS, pi)
            } catch (_: Throwable) {}
        }
        
        private fun cancelSchedule(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SpeedTimeWidgetProvider::class.java).apply {
                    action = ACTION_TICK
                }
                val pi = PendingIntent.getBroadcast(
                    context, REQ_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.cancel(pi)
            } catch (_: Throwable) {}
        }
    }
}
