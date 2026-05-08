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

/**
 * v50: Widget Speed Stats 2x4 — versione SEMPLIFICATA che funziona.
 * Niente più tinting dinamico runtime (che richiedeva API 31+ e fallava su molti device).
 * Drawable rounded fissi (verde RAM / blu storage / giallo battery) in 2 varianti tema (dark/light).
 * Background widget secondo tema (system/transparent/light/dark).
 * Auto-refresh on/off + bottone refresh manuale.
 * Sottotitolo batteria mostra mAh se device lo supporta.
 */
class SpeedStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, ids: IntArray
    ) {
        for (id in ids) updateWidget(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }
    }

    private fun updateWidget(
        context: Context, manager: AppWidgetManager, id: Int
    ) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME, THEME_TRANSPARENT) ?: THEME_TRANSPARENT
        val autoRefresh = prefs.getBoolean(KEY_AUTO_REFRESH, true)

        val isLight = when (theme) {
            THEME_LIGHT -> true
            THEME_DARK -> false
            THEME_TRANSPARENT -> false
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                nightMode != Configuration.UI_MODE_NIGHT_YES
            }
        }

        // Scelgo layout in base al tema chiaro/scuro (i progress drawable sono diversi)
        val layoutRes = if (isLight && theme != THEME_TRANSPARENT)
            R.layout.widget_speed_stats_light
        else
            R.layout.widget_speed_stats

        val views = RemoteViews(context.packageName, layoutRes)

        // Background secondo tema
        val bgRes = when (theme) {
            THEME_TRANSPARENT -> R.drawable.bg_widget_speed_stats_transparent
            THEME_LIGHT -> R.drawable.bg_widget_speed_stats_light
            THEME_DARK -> R.drawable.bg_widget_speed_stats
            else -> if (isLight) R.drawable.bg_widget_speed_stats_light
                    else R.drawable.bg_widget_speed_stats
        }
        views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)

        // === RAM ===
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val ramAvailMb = mi.availMem / (1024 * 1024)
        val ramTotMb = mi.totalMem / (1024 * 1024)
        val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
        views.setTextViewText(R.id.ramPct, "$ramPct%")
        views.setTextViewText(R.id.ramSubtitle, "${ramAvailMb} MB")
        views.setProgressBar(R.id.ramProgress, 100, ramPct, false)

        // === Storage ===
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
        val availGb = availBytes / (1024 * 1024 * 1024)
        views.setTextViewText(R.id.storPct, "$storPct%")
        views.setTextViewText(R.id.storSubtitle, "${availGb} GB")
        views.setProgressBar(R.id.storProgress, 100, storPct, false)

        // === Battery ===
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        views.setTextViewText(R.id.battPct, "$battPct%")
        views.setProgressBar(R.id.battProgress, 100, battPct, false)

        val charging = bm.isCharging
        // Sottotitolo: prova mAh, fallback a "In carica" o "Batteria"
        val chargeCounter = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (_: Throwable) { Int.MIN_VALUE }
        val mahText = if (chargeCounter > 0 && chargeCounter != Int.MIN_VALUE) {
            "${chargeCounter / 1000} mAh"
        } else if (charging) {
            context.getString(R.string.widget_charging)
        } else {
            context.getString(R.string.widget_battery)
        }
        views.setTextViewText(R.id.battSubtitle, mahText)

        // Bottone refresh: visibile SOLO se auto-refresh è OFF
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

        // Tap → apre launcher
        val rootPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, rootPi)

        manager.updateAppWidget(id, views)
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
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, SpeedStatsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
