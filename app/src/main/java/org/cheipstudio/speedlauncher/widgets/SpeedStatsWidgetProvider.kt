package org.cheipstudio.speedlauncher.widgets

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * v297: MINIMAL Speed Widget provider per debug.
 * Solo setTextViewText. Niente altro. Se questo funziona, costruiamo sopra.
 */
class SpeedStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidgetSafe(context, manager, id) }
            catch (t: Throwable) { logError(context, "onUpdate id=$id", t) }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
        try { updateWidgetSafe(context, manager, id) }
        catch (t: Throwable) { logError(context, "optionsChanged id=$id", t) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            if (intent.action == ACTION_REFRESH) {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
                for (id in ids) {
                    try { updateWidgetSafe(context, mgr, id) }
                    catch (t: Throwable) { logError(context, "refresh id=$id", t) }
                }
            }
        } catch (t: Throwable) {
            logError(context, "onReceive top", t)
        }
    }

    private fun updateWidgetSafe(context: Context, manager: AppWidgetManager, id: Int) {
        // MINIMAL: solo setTextViewText sui 3 valori. Niente altro.
        val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)
        
        // RAM
        try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            val ramAvailMb = mi.availMem / (1024 * 1024)
            val ramTotMb = mi.totalMem / (1024 * 1024)
            val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
            views.setTextViewText(R.id.col1_value, "$ramPct%")
            views.setTextViewText(R.id.col1_label, "RAM")
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col1_value, "—")
        }
        
        // Storage
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
            val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
            views.setTextViewText(R.id.col2_value, "$storPct%")
            views.setTextViewText(R.id.col2_label, "MEM")
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col2_value, "—")
        }
        
        // Battery
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            views.setTextViewText(R.id.col3_value, "$battPct%")
            views.setTextViewText(R.id.col3_label, "BAT")
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col3_value, "—")
        }
        
        // Tap launcher
        try {
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)
        } catch (_: Throwable) {}
        
        manager.updateAppWidget(id, views)
    }

    private fun logError(context: Context, tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val log = "[$tag] ${t.javaClass.simpleName}: ${t.message}\n$sw\n---\n"
            val file = File(context.cacheDir, "speed_widget_errors.log")
            if (file.length() > 50_000) file.delete()
            file.appendText(log)
        } catch (_: Throwable) {}
    }

    companion object {
        const val ACTION_REFRESH = "org.cheipstudio.speedlauncher.WIDGET_REFRESH"
        const val SETTINGS_PREFS = "speed_widget_prefs"
        const val KEY_THEME = "widget_theme"
        const val KEY_AUTO_REFRESH = "widget_auto_refresh"
        const val KEY_SECTIONS_PREFIX = "widget_sections_"
        const val THEME_SYSTEM = "system"
        const val THEME_TRANSPARENT = "transparent"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        
        const val SECTION_RAM = "ram"
        const val SECTION_STORAGE = "storage"
        const val SECTION_BATTERY = "battery"
        const val SECTION_DATE = "date"
        const val SECTION_TIME = "time"
        const val SECTION_WIFI = "wifi"
        const val SECTION_VOLUME = "volume"
        const val SECTION_BRIGHTNESS = "brightness"
        val ALL_SECTIONS = listOf(SECTION_RAM, SECTION_STORAGE, SECTION_BATTERY, 
                                  SECTION_DATE, SECTION_TIME, SECTION_WIFI, 
                                  SECTION_VOLUME, SECTION_BRIGHTNESS)
        val DEFAULT_SECTIONS = arrayOf(SECTION_RAM, SECTION_STORAGE, SECTION_BATTERY)

        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val intent = Intent(context, SpeedStatsWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
        
        fun saveSections(context: Context, widgetId: Int, sections: Array<String>) {
            try {
                if (sections.size != 3) return
                context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .edit().putString("$KEY_SECTIONS_PREFIX$widgetId", sections.joinToString(",")).apply()
            } catch (_: Throwable) {}
        }
    }
}
