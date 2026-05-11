package org.cheipstudio.speedlauncher.widgets

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
 * v303: Speed Widget Material Expressive con donut rings.
 * Bitmap dim ridotta (62dp), niente setViewVisibility (safe RemoteViews).
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

        // RAM
        val ramPct = readRamPct(context)
        views.setImageViewBitmap(R.id.col1_ring, renderDonutRing(context, ramPct, COLOR_RAM, isLight))
        views.setTextViewText(R.id.col1_subtitle, readRamSubtitle(context))
        
        // Memoria
        val storPct = readStoragePct()
        views.setImageViewBitmap(R.id.col2_ring, renderDonutRing(context, storPct, COLOR_STORAGE, isLight))
        views.setTextViewText(R.id.col2_subtitle, readStorageSubtitle())
        
        // Battery
        val battPct = readBatteryPct(context)
        views.setImageViewBitmap(R.id.col3_ring, renderDonutRing(context, battPct, COLOR_BATTERY, isLight))
        views.setTextViewText(R.id.col3_subtitle, readBatterySubtitle(context))
        
        // Tap
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

    /** Bitmap donut ring 62dp con valore al centro */
    private fun renderDonutRing(context: Context, pct: Int, color: Int, isLight: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (62 * density).toInt().coerceAtLeast(120).coerceAtMost(300)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        
        val strokeW = 7f * density
        val pad = strokeW / 2f + 2f
        val rect = RectF(pad, pad, size - pad, size - pad)
        
        val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            this.color = if (isLight) 0x1A000000 else 0x33FFFFFF
        }
        canvas.drawArc(rect, 0f, 360f, false, paintTrack)
        
        val paintProg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        val sweep = (pct.coerceIn(0, 100) * 360f / 100f)
        canvas.drawArc(rect, -90f, sweep, false, paintProg)
        
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (isLight) Color.parseColor("#1A1A1A") else Color.WHITE
            textSize = 17 * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = size / 2f - (paintText.fontMetrics.ascent + paintText.fontMetrics.descent) / 2f
        canvas.drawText("$pct%", size / 2f, textY, paintText)
        
        return bmp
    }
    
    private fun readRamPct(context: Context): Int = try {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        val avail = mi.availMem / (1024 * 1024)
        val tot = mi.totalMem / (1024 * 1024)
        if (tot > 0) 100 - ((avail * 100) / tot).toInt() else 0
    } catch (_: Throwable) { 0 }

    private fun readRamSubtitle(context: Context): String = try {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        "${mi.availMem / (1024 * 1024)} MB"
    } catch (_: Throwable) { "" }

    private fun readStoragePct(): Int = try {
        val s = StatFs(Environment.getDataDirectory().path)
        val tot = s.blockCountLong * s.blockSizeLong
        val avail = s.availableBlocksLong * s.blockSizeLong
        if (tot > 0) 100 - ((avail * 100) / tot).toInt() else 0
    } catch (_: Throwable) { 0 }

    private fun readStorageSubtitle(): String = try {
        val s = StatFs(Environment.getDataDirectory().path)
        val avail = s.availableBlocksLong * s.blockSizeLong
        "${avail / (1024 * 1024 * 1024)} GB"
    } catch (_: Throwable) { "" }

    private fun readBatteryPct(context: Context): Int = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    } catch (_: Throwable) { 0 }

    private fun readBatterySubtitle(context: Context): String = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val charge = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        if (charge > 0 && charge != Int.MIN_VALUE) "${charge / 1000} mAh" else ""
    } catch (_: Throwable) { "" }

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
        
        const val COLOR_RAM = 0xFF4ADE80.toInt()
        const val COLOR_STORAGE = 0xFF60A5FA.toInt()
        const val COLOR_BATTERY = 0xFFFB923C.toInt()

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
