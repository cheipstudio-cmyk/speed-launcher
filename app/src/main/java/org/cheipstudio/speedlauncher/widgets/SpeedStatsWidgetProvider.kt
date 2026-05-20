package org.cheipstudio.speedlauncher.widgets

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
 * v304: Speed Widget - design Material Expressive con icone vector + progress bar lineari.
 * NIENTE bitmap (causa fallimento RemoteViews bind). 3 sezioni fisse RAM/Memoria/Batteria.
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
        val theme = readTheme(context)
        val isLight = isLightTheme(context, theme)
        val layoutRes = when {
            theme == THEME_TRANSPARENT -> R.layout.widget_speed_stats_transparent
            isLight -> R.layout.widget_speed_stats_light
            else -> R.layout.widget_speed_stats
        }
        val views = RemoteViews(context.packageName, layoutRes)
        
        // v305: altezza adattiva
        val widgetMinH = try {
            manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 130) ?: 130
        } catch (_: Throwable) { 130 }
        applyVisibility(views, widgetMinH)

        // RAM
        try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            val avail = mi.availMem / (1024 * 1024)
            val tot = mi.totalMem / (1024 * 1024)
            val pct = if (tot > 0) 100 - ((avail * 100) / tot).toInt() else 0
            views.setTextViewText(R.id.col1_value, "$pct%")
            views.setTextViewText(R.id.col1_subtitle, "$avail MB liberi")
            views.setProgressBar(R.id.col1_progress, 100, pct, false)
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col1_value, "—")
            views.setTextViewText(R.id.col1_subtitle, "")
        }
        
        // Memoria
        try {
            val s = StatFs(Environment.getDataDirectory().path)
            val tot = s.blockCountLong * s.blockSizeLong
            val avail = s.availableBlocksLong * s.blockSizeLong
            val pct = if (tot > 0) 100 - ((avail * 100) / tot).toInt() else 0
            val availGb = avail / (1024 * 1024 * 1024)
            views.setTextViewText(R.id.col2_value, "$pct%")
            views.setTextViewText(R.id.col2_subtitle, "$availGb GB liberi")
            views.setProgressBar(R.id.col2_progress, 100, pct, false)
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col2_value, "—")
            views.setTextViewText(R.id.col2_subtitle, "")
        }
        
        // Battery
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            val charge = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
            } catch (_: Throwable) { Int.MIN_VALUE }
            views.setTextViewText(R.id.col3_value, "$pct%")
            val sub = if (charge > 0 && charge != Int.MIN_VALUE) "${charge / 1000} mAh" else ""
            views.setTextViewText(R.id.col3_subtitle, sub)
            views.setProgressBar(R.id.col3_progress, 100, pct, false)
        } catch (_: Throwable) {
            views.setTextViewText(R.id.col3_value, "—")
            views.setTextViewText(R.id.col3_subtitle, "")
        }
        
        // v319: niente PendingIntent on click - il widget è sulla home, tap non fa nulla
        // (evita che MainActivity appaia nelle recents come task duplicato)
        
        manager.updateAppWidget(id, views)
    }

    private fun applyVisibility(views: RemoteViews, widgetMinH: Int) {
        try {
            // < 60dp: solo valore grande (no icon, no progress, no label, no subtitle)
            // 60-89: icon + value (no progress, no label, no subtitle)
            // 90-129: icon + value + progress + label (no subtitle)
            // 130+: tutto
            val showIcon = widgetMinH >= 60
            val showProgress = widgetMinH >= 90
            val showLabel = widgetMinH >= 90
            val showSubtitle = widgetMinH >= 130
            val vis = { v: Boolean -> if (v) android.view.View.VISIBLE else android.view.View.GONE }
            views.setViewVisibility(R.id.col1_icon, vis(showIcon))
            views.setViewVisibility(R.id.col2_icon, vis(showIcon))
            views.setViewVisibility(R.id.col3_icon, vis(showIcon))
            views.setViewVisibility(R.id.col1_progress, vis(showProgress))
            views.setViewVisibility(R.id.col2_progress, vis(showProgress))
            views.setViewVisibility(R.id.col3_progress, vis(showProgress))
            views.setViewVisibility(R.id.col1_label, vis(showLabel))
            views.setViewVisibility(R.id.col2_label, vis(showLabel))
            views.setViewVisibility(R.id.col3_label, vis(showLabel))
            views.setViewVisibility(R.id.col1_subtitle, vis(showSubtitle))
            views.setViewVisibility(R.id.col2_subtitle, vis(showSubtitle))
            views.setViewVisibility(R.id.col3_subtitle, vis(showSubtitle))
        } catch (_: Throwable) {}
    }
    
    private fun readTheme(context: Context): String = try {
        val raw = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_TRANSPARENT)
        if (raw.isNullOrBlank() || raw !in setOf(THEME_SYSTEM, THEME_TRANSPARENT, THEME_LIGHT, THEME_DARK))
            THEME_TRANSPARENT else raw
    } catch (_: Throwable) { THEME_TRANSPARENT }
    
    private fun isLightTheme(context: Context, theme: String): Boolean = when (theme) {
        THEME_LIGHT -> true
        THEME_DARK, THEME_TRANSPARENT -> false
        else -> try {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode != Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) { false }
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
