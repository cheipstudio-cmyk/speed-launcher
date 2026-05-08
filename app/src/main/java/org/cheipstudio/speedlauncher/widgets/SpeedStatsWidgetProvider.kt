package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.StatFs
import android.app.ActivityManager
import android.os.Environment
import android.view.View
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * v53: Widget Speed Stats — versione DEFENSIVE.
 * Tutto il flusso wrappato in try/catch principale che, se fallisce,
 * renderizza un widget minimo invece di lasciare crashare → "Couldn't add widget".
 * Log degli errori in cache app per debug.
 */
class SpeedStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, ids: IntArray
    ) {
        for (id in ids) {
            try {
                updateWidgetSafe(context, manager, id)
            } catch (t: Throwable) {
                logError(context, "onUpdate id=$id", t)
                tryRenderFallback(context, manager, id, t)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            if (intent.action == ACTION_REFRESH) {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
                for (id in ids) {
                    try { updateWidgetSafe(context, mgr, id) }
                    catch (t: Throwable) {
                        logError(context, "onReceive REFRESH id=$id", t)
                        tryRenderFallback(context, mgr, id, t)
                    }
                }
            }
        } catch (t: Throwable) {
            logError(context, "onReceive top-level", t)
        }
    }

    private fun updateWidgetSafe(
        context: Context, manager: AppWidgetManager, id: Int
    ) {
        // Safe defaults se prefs corrotte/null
        val theme = try {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_THEME, THEME_TRANSPARENT)
            if (raw.isNullOrBlank()) THEME_TRANSPARENT
            else if (raw !in setOf(THEME_SYSTEM, THEME_TRANSPARENT, THEME_LIGHT, THEME_DARK)) THEME_TRANSPARENT
            else raw
        } catch (_: Throwable) { THEME_TRANSPARENT }

        val autoRefresh = try {
            context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_REFRESH, true)
        } catch (_: Throwable) { true }

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
            R.layout.widget_speed_stats_light
        else
            R.layout.widget_speed_stats

        val views = RemoteViews(context.packageName, layoutRes)

        // Background
        try {
            val bgRes = when (theme) {
                THEME_TRANSPARENT -> R.drawable.bg_widget_speed_stats_transparent
                THEME_LIGHT -> R.drawable.bg_widget_speed_stats_light
                THEME_DARK -> R.drawable.bg_widget_speed_stats
                else -> if (isLight) R.drawable.bg_widget_speed_stats_light
                        else R.drawable.bg_widget_speed_stats
            }
            views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)
        } catch (_: Throwable) {}

        // RAM
        try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            val ramAvailMb = mi.availMem / (1024 * 1024)
            val ramTotMb = mi.totalMem / (1024 * 1024)
            val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
            views.setTextViewText(R.id.ramPct, "$ramPct%")
            views.setTextViewText(R.id.ramSubtitle, "${ramAvailMb} MB")
            views.setProgressBar(R.id.ramProgress, 100, ramPct, false)
        } catch (t: Throwable) {
            logError(context, "RAM block", t)
            views.setTextViewText(R.id.ramPct, "—")
            views.setTextViewText(R.id.ramSubtitle, "")
        }

        // Storage
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
            val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
            val availGb = availBytes / (1024 * 1024 * 1024)
            views.setTextViewText(R.id.storPct, "$storPct%")
            views.setTextViewText(R.id.storSubtitle, "${availGb} GB")
            views.setProgressBar(R.id.storProgress, 100, storPct, false)
        } catch (t: Throwable) {
            logError(context, "Storage block", t)
            views.setTextViewText(R.id.storPct, "—")
            views.setTextViewText(R.id.storSubtitle, "")
        }

        // Battery
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            views.setTextViewText(R.id.battPct, "$battPct%")
            views.setProgressBar(R.id.battProgress, 100, battPct, false)

            val charging = try { bm?.isCharging == true } catch (_: Throwable) { false }
            val chargeCounter = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
            } catch (_: Throwable) { Int.MIN_VALUE }

            val mahText = if (chargeCounter > 0 && chargeCounter != Int.MIN_VALUE) {
                "${chargeCounter / 1000} mAh"
            } else if (charging) {
                try { context.getString(R.string.widget_charging) } catch (_: Throwable) { "" }
            } else {
                try { context.getString(R.string.widget_battery) } catch (_: Throwable) { "" }
            }
            views.setTextViewText(R.id.battSubtitle, mahText)
        } catch (t: Throwable) {
            logError(context, "Battery block", t)
            views.setTextViewText(R.id.battPct, "—")
            views.setTextViewText(R.id.battSubtitle, "")
        }

        // Refresh button
        try {
            if (!autoRefresh) {
                views.setViewVisibility(R.id.refreshBtn, View.VISIBLE)
                val btnBg = if (isLight && theme != THEME_TRANSPARENT)
                    R.drawable.bg_widget_refresh_btn_light
                else R.drawable.bg_widget_refresh_btn
                views.setInt(R.id.refreshBtn, "setBackgroundResource", btnBg)
                val refreshIntent = Intent(context, SpeedStatsWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val pi = PendingIntent.getBroadcast(
                    context, 1, refreshIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.refreshBtn, pi)
            } else {
                views.setViewVisibility(R.id.refreshBtn, View.GONE)
            }
        } catch (t: Throwable) {
            logError(context, "Refresh btn", t)
        }

        // Tap → launcher
        try {
            val rootPi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, rootPi)
        } catch (t: Throwable) {
            logError(context, "Root tap intent", t)
        }

        manager.updateAppWidget(id, views)
    }

    /** Renderizza un widget minimo "Speed Stats" senza dati, per non lasciare il sistema mostrare errore */
    private fun tryRenderFallback(
        context: Context, manager: AppWidgetManager, id: Int, error: Throwable
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)
            views.setTextViewText(R.id.ramPct, "—")
            views.setTextViewText(R.id.storPct, "—")
            views.setTextViewText(R.id.battPct, "—")
            views.setTextViewText(R.id.ramSubtitle, "")
            views.setTextViewText(R.id.storSubtitle, "")
            views.setTextViewText(R.id.battSubtitle, "")
            manager.updateAppWidget(id, views)
        } catch (_: Throwable) {}
    }

    /** Log su cache dell'app per debug */
    private fun logError(context: Context, tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val log = "[$tag] ${t.javaClass.simpleName}: ${t.message}\n$sw\n---\n"
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "speed_widget_errors.log")
            // Append, ma cap a ~50KB per non riempire
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
