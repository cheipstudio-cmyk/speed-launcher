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
 * v300: Speed Widget - 3 sezioni fisse (RAM, Memoria, Batteria) con design Material Expressive.
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
        val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)
        
        // RAM
        try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            val ramAvailMb = mi.availMem / (1024 * 1024)
            val ramTotMb = mi.totalMem / (1024 * 1024)
            val ramPct = if (ramTotMb > 0) 100 - ((ramAvailMb * 100) / ramTotMb).toInt() else 0
            views.setTextViewText(R.id.col1_value, "$ramPct%")
            views.setTextViewText(R.id.col1_subtitle, "${ramAvailMb} MB")
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col1_value, "—")
        }
        
        // Memoria
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
            val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val storPct = if (totalBytes > 0) 100 - ((availBytes * 100) / totalBytes).toInt() else 0
            val availGb = availBytes / (1024 * 1024 * 1024)
            views.setTextViewText(R.id.col2_value, "$storPct%")
            views.setTextViewText(R.id.col2_subtitle, "${availGb} GB")
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col2_value, "—")
        }
        
        // Battery
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            views.setTextViewText(R.id.col3_value, "$battPct%")
            val chargeCounter = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
            } catch (_: Throwable) { Int.MIN_VALUE }
            val subtitle = if (chargeCounter > 0 && chargeCounter != Int.MIN_VALUE) {
                "${chargeCounter / 1000} mAh"
            } else ""
            views.setTextViewText(R.id.col3_subtitle, subtitle)
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col3_value, "—")
        }
        
        // Tap → launcher
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
        const val THEME_SYSTEM = "system"
        const val THEME_TRANSPARENT = "transparent"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

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
    }
}
