package org.cheipstudio.speedlauncher.widgets

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.Locale

/**
 * v298: Speed Widget configurabile.
 * 3 colonne fisse. Utente sceglie 3 sezioni tra: RAM, Storage, Battery, Date, Time, WiFi, Volume, Brightness.
 * Layout pulito. Solo setTextViewText + setImageViewResource + setOnClickPendingIntent.
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
        
        val sections = readSections(context, id)
        
        renderColumn(context, views, 1, sections[0])
        renderColumn(context, views, 2, sections[1])
        renderColumn(context, views, 3, sections[2])
        
        // Tap sul widget background → launcher (le colonne hanno tap diverso se configurato)
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

    private fun renderColumn(context: Context, views: RemoteViews, colIdx: Int, section: String) {
        val ids = colIds(colIdx)
        try {
            when (section) {
                SECTION_RAM -> renderRam(context, views, ids)
                SECTION_STORAGE -> renderStorage(context, views, ids)
                SECTION_BATTERY -> renderBattery(context, views, ids)
                SECTION_DATE -> renderDate(context, views, ids)
                SECTION_TIME -> renderTime(context, views, ids)
                SECTION_WIFI -> renderWifi(context, views, ids)
                SECTION_VOLUME -> renderVolume(context, views, ids)
                SECTION_BRIGHTNESS -> renderBrightness(context, views, ids)
                else -> renderRam(context, views, ids)
            }
            // Tap per colonna se ha azione associata
            val tapIntent = tapIntentForSection(section)
            if (tapIntent != null) {
                val pi = PendingIntent.getActivity(
                    context, 100 + colIdx, tapIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(ids.root, pi)
            }
        } catch (t: Throwable) {
            logError(context, "renderColumn col=$colIdx section=$section", t)
            try { views.setTextViewText(ids.value, "—") } catch (_: Throwable) {}
        }
    }

    // ============= SECTION RENDERERS =============

    private fun renderRam(context: Context, views: RemoteViews, ids: ColIds) {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        val ramAvailMb = mi.availMem / (1024 * 1024)
        val ramTotMb = mi.totalMem / (1024 * 1024)
        val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
        views.setTextViewText(ids.value, "$ramPct%")
        views.setTextViewText(ids.label, "RAM")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_memory)
    }

    private fun renderStorage(context: Context, views: RemoteViews, ids: ColIds) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
        views.setTextViewText(ids.value, "$storPct%")
        views.setTextViewText(ids.label, "MEM")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_storage)
    }

    private fun renderBattery(context: Context, views: RemoteViews, ids: ColIds) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        views.setTextViewText(ids.value, "$battPct%")
        views.setTextViewText(ids.label, "BAT")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_battery)
    }

    private fun renderDate(context: Context, views: RemoteViews, ids: ColIds) {
        val cal = Calendar.getInstance()
        val locale = Locale.getDefault()
        val dayShort = android.text.format.DateFormat.format("EEE", cal).toString()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        views.setTextViewText(ids.value, "$dayShort $dayNum")
        views.setTextViewText(ids.label, "DATA")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_date)
    }

    private fun renderTime(context: Context, views: RemoteViews, ids: ColIds) {
        val cal = Calendar.getInstance()
        val is24 = DateFormat.is24HourFormat(context)
        val fmt = if (is24) "HH:mm" else "h:mm"
        val timeStr = android.text.format.DateFormat.format(fmt, cal).toString()
        views.setTextViewText(ids.value, timeStr)
        views.setTextViewText(ids.label, "ORA")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_time)
    }

    private fun renderWifi(context: Context, views: RemoteViews, ids: ColIds) {
        val ssid = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wm?.connectionInfo
            val raw = info?.ssid ?: ""
            raw.trim('"').takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
        } catch (_: Throwable) { null }
        val display = ssid ?: "OFF"
        val truncated = if (display.length > 8) display.substring(0, 7) + "…" else display
        views.setTextViewText(ids.value, truncated)
        views.setTextViewText(ids.label, "WIFI")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_wifi)
    }

    private fun renderVolume(context: Context, views: RemoteViews, ids: ColIds) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val current = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        val pct = if (max > 0) ((current * 100) / max) else 0
        views.setTextViewText(ids.value, "$pct%")
        views.setTextViewText(ids.label, "VOL")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_volume)
    }

    private fun renderBrightness(context: Context, views: RemoteViews, ids: ColIds) {
        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Throwable) { 0 }
        val pct = ((brightness * 100) / 255).coerceIn(0, 100)
        views.setTextViewText(ids.value, "$pct%")
        views.setTextViewText(ids.label, "LUM")
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_brightness)
    }

    // ============= TAP HANDLERS =============

    private fun tapIntentForSection(section: String): Intent? {
        return try {
            when (section) {
                SECTION_BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                SECTION_DATE -> Intent(Intent.ACTION_VIEW).apply {
                    val builder = CalendarContract.CONTENT_URI.buildUpon()
                    builder.appendPath("time")
                    builder.appendPath(System.currentTimeMillis().toString())
                    data = builder.build()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                SECTION_TIME -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                SECTION_WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                SECTION_VOLUME -> Intent(Settings.ACTION_SOUND_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                SECTION_BRIGHTNESS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                else -> null
            }
        } catch (_: Throwable) { null }
    }

    // ============= CONFIG STORAGE =============

    private fun readSections(context: Context, widgetId: Int): Array<String> {
        return try {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString("$KEY_SECTIONS_PREFIX$widgetId", null)
            if (raw.isNullOrBlank()) DEFAULT_SECTIONS.copyOf()
            else {
                val parts = raw.split(",")
                if (parts.size == 3) arrayOf(
                    sanitizeSection(parts[0]),
                    sanitizeSection(parts[1]),
                    sanitizeSection(parts[2])
                ) else DEFAULT_SECTIONS.copyOf()
            }
        } catch (_: Throwable) { DEFAULT_SECTIONS.copyOf() }
    }

    private fun sanitizeSection(s: String): String = if (s in ALL_SECTIONS) s else SECTION_RAM

    private fun colIds(colIdx: Int): ColIds = when (colIdx) {
        1 -> ColIds(R.id.col1_root, R.id.col1_icon, R.id.col1_value, R.id.col1_label)
        2 -> ColIds(R.id.col2_root, R.id.col2_icon, R.id.col2_value, R.id.col2_label)
        else -> ColIds(R.id.col3_root, R.id.col3_icon, R.id.col3_value, R.id.col3_label)
    }

    private data class ColIds(val root: Int, val icon: Int, val value: Int, val label: Int)

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
