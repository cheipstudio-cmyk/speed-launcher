package org.cheipstudio.speedlauncher.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
 * v186: Widget Speed Time. TextView semplice, refresh via AlarmManager 1 min.
 * Tema sincronizzato con Speed Stats (system/light/dark/transparent).
 */
class SpeedTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidget(context, mgr, id) } catch (_: Throwable) {}
        }
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelSchedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedTimeWidgetProvider::class.java))
                for (id in ids) updateWidget(context, mgr, id)
            } catch (_: Throwable) {}
            scheduleNext(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "org.cheipstudio.speedlauncher.WIDGET_TIME_TICK"
        private const val REQ_CODE = 8421
        
        // v186: tema sincronizzato col widget speed_stats
        private const val PREFS_NAME = "speed_widget_prefs"
        private const val KEY_THEME = "widget_theme"
        private const val THEME_SYSTEM = "system"
        private const val THEME_TRANSPARENT = "transparent"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
        
        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedTimeWidgetProvider::class.java))
                for (id in ids) updateWidget(context, mgr, id)
            } catch (_: Throwable) {}
        }
        
        fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            // Theme detection
            val theme = try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_THEME, THEME_TRANSPARENT)
                if (raw.isNullOrBlank()) THEME_TRANSPARENT
                else if (raw !in setOf(THEME_SYSTEM, THEME_TRANSPARENT, THEME_LIGHT, THEME_DARK)) THEME_TRANSPARENT
                else raw
            } catch (_: Throwable) { THEME_TRANSPARENT }
            
            val isLight = when (theme) {
                THEME_LIGHT -> true
                THEME_DARK -> false
                THEME_TRANSPARENT -> false
                else -> {
                    try {
                        val nightMode = context.resources.configuration.uiMode and 
                            Configuration.UI_MODE_NIGHT_MASK
                        nightMode != Configuration.UI_MODE_NIGHT_YES
                    } catch (_: Throwable) { false }
                }
            }
            
            // Layout selection
            val layoutRes = if (isLight && theme != THEME_TRANSPARENT)
                R.layout.widget_speed_time_light
            else
                R.layout.widget_speed_time
            
            val views = RemoteViews(context.packageName, layoutRes)
            
            // Background per tema
            try {
                val bgRes = when (theme) {
                    THEME_TRANSPARENT -> R.drawable.widget_speed_time_bg_transparent
                    THEME_LIGHT -> R.drawable.widget_speed_time_bg_light
                    THEME_DARK -> R.drawable.widget_speed_time_bg
                    else -> if (isLight) R.drawable.widget_speed_time_bg_light
                            else R.drawable.widget_speed_time_bg
                }
                views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)
            } catch (_: Throwable) {}
            
            val now = Date()
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            views.setTextViewText(R.id.timeText, timeFmt.format(now))
            
            val dayFmt = SimpleDateFormat("d", Locale.getDefault())
            val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
            views.setTextViewText(R.id.dateDay, dayFmt.format(now))
            views.setTextViewText(R.id.dateMonth, monthFmt.format(now).uppercase(Locale.getDefault()))
            
            val pct = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Throwable) { -1 }
            views.setTextViewText(R.id.batteryText, if (pct >= 0) "$pct%" else "--")
            
            // Click intents
            try {
                val timeIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { 
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK 
                }
                views.setOnClickPendingIntent(R.id.timeBlock, PendingIntent.getActivity(
                    context, 1001, timeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            
            try {
                val calUri = CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
                val dateIntent = Intent(Intent.ACTION_VIEW, calUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                views.setOnClickPendingIntent(R.id.dateBlock, PendingIntent.getActivity(
                    context, 1002, dateIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            
            try {
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
            } catch (_: Throwable) {}
            
            mgr.updateAppWidget(id, views)
        }
        
        private fun scheduleNext(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SpeedTimeWidgetProvider::class.java).apply {
                    action = ACTION_TICK
                }
                val pi = PendingIntent.getBroadcast(
                    context, REQ_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val now = System.currentTimeMillis()
                val nextMinute = ((now / 60_000L) + 1) * 60_000L
                am.set(AlarmManager.RTC, nextMinute, pi)
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
