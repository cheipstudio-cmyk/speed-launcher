package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.BatteryManager
import android.os.StatFs
import android.app.ActivityManager
import android.os.Environment
import android.view.View
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R

/**
 * v44/v47/v48: Widget Speed Stats 2x4.
 * v48: progress bar gradient (rosso <15% → verde 100%), batteria mAh disponibili,
 *       drawable unificato con tint dinamico.
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

        val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)

        // Background secondo tema
        val bgRes = when (theme) {
            THEME_TRANSPARENT -> R.drawable.bg_widget_speed_stats_transparent
            THEME_LIGHT -> R.drawable.bg_widget_speed_stats_light
            THEME_DARK -> R.drawable.bg_widget_speed_stats
            else -> if (isLight) R.drawable.bg_widget_speed_stats_light
                    else R.drawable.bg_widget_speed_stats
        }
        views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)

        // Colori testo secondo tema
        val titleColor = if (isLight) 0xFF666677.toInt() else 0xFFB0B0BD.toInt()
        val pctColor = if (isLight) 0xFF1A1A1F.toInt() else 0xFFFFFFFF.toInt()
        val subColor = if (isLight) 0xFF888899.toInt() else 0xFFB0B0BD.toInt()

        views.setTextColor(R.id.ramLabel, titleColor)
        views.setTextColor(R.id.storLabel, titleColor)
        views.setTextColor(R.id.battLabel, titleColor)
        views.setTextColor(R.id.ramPct, pctColor)
        views.setTextColor(R.id.storPct, pctColor)
        views.setTextColor(R.id.battPct, pctColor)
        views.setTextColor(R.id.ramSubtitle, subColor)
        views.setTextColor(R.id.storSubtitle, subColor)
        views.setTextColor(R.id.battSubtitle, subColor)

        // Progress drawable: unified, poi tinto dinamicamente
        val unifiedDrawable = if (isLight && theme != THEME_TRANSPARENT)
            R.drawable.progress_widget_unified_light
        else R.drawable.progress_widget_unified
        views.setInt(R.id.ramProgress, "setProgressDrawableResource", unifiedDrawable)
        views.setInt(R.id.storProgress, "setProgressDrawableResource", unifiedDrawable)
        views.setInt(R.id.battProgress, "setProgressDrawableResource", unifiedDrawable)

        // === RAM ===
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val ramAvailMb = mi.availMem / (1024 * 1024)
        val ramTotMb = mi.totalMem / (1024 * 1024)
        val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
        views.setTextViewText(R.id.ramPct, "$ramPct%")
        views.setTextViewText(R.id.ramSubtitle, "${ramAvailMb} MB")
        views.setProgressBar(R.id.ramProgress, 100, ramPct, false)
        views.setColorStateList(R.id.ramProgress, "setProgressTintList",
            ColorStateList.valueOf(gradientColor(ramPct)))

        // === Storage ===
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
        val availGb = availBytes / (1024 * 1024 * 1024)
        views.setTextViewText(R.id.storPct, "$storPct%")
        views.setTextViewText(R.id.storSubtitle, "${availGb} GB")
        views.setProgressBar(R.id.storProgress, 100, storPct, false)
        views.setColorStateList(R.id.storProgress, "setProgressTintList",
            ColorStateList.valueOf(gradientColor(storPct)))

        // === Battery ===
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        views.setTextViewText(R.id.battPct, "$battPct%")
        views.setProgressBar(R.id.battProgress, 100, battPct, false)
        views.setColorStateList(R.id.battProgress, "setProgressTintList",
            ColorStateList.valueOf(gradientColor(battPct)))

        // v48: subtitle batteria con mAh disponibili (se device lo supporta)
        // BATTERY_PROPERTY_CHARGE_COUNTER ritorna µAh (microampere/ora)
        val chargeCounter = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (_: Throwable) { Int.MIN_VALUE }
        val charging = bm.isCharging
        val mahText = if (chargeCounter > 0 && chargeCounter != Int.MIN_VALUE) {
            val mah = chargeCounter / 1000
            "${mah} mAh"
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

        // Tap sul widget root → apre launcher
        val rootPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, rootPi)

        manager.updateAppWidget(id, views)
    }

    /**
     * v48: gradient rosso → giallo → verde basato su percentuale.
     * < 15% → rosso pieno
     * 15..50% → rosso → giallo
     * 50..100% → giallo → verde
     */
    private fun gradientColor(pct: Int): Int {
        val p = pct.coerceIn(0, 100)
        return if (p < 15) {
            Color.parseColor("#E53935")  // rosso
        } else {
            // Lerp da rosso (#E53935 a 15%) a verde (#43A047 a 100%) via HSV
            // Hue: 0 (rosso) → 120 (verde) lineare
            val t = ((p - 15).toFloat() / 85f).coerceIn(0f, 1f)
            val hue = t * 120f  // 0=rosso, 60=giallo, 120=verde
            val hsv = floatArrayOf(hue, 0.7f, 0.85f)
            Color.HSVToColor(hsv)
        }
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
