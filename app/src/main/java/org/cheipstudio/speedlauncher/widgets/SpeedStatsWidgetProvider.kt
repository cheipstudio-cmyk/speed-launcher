package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.StatFs
import android.app.ActivityManager
import android.os.Environment
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.Locale

/**
 * v286: Widget configurabile - "Speed Widget".
 * 3 colonne fisse, ognuna riempita da una delle 8 sezioni disponibili.
 * Config persistita per widgetId in SharedPreferences.
 */
class SpeedStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidgetSafe(context, manager, id) }
            catch (t: Throwable) {
                logError(context, "onUpdate id=$id", t)
                tryRenderFallback(context, manager, id, t)
            }
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

    private fun updateWidgetSafe(context: Context, manager: AppWidgetManager, id: Int) {
        val theme = readTheme(context)
        val autoRefresh = readAutoRefresh(context)
        val isLight = isLightTheme(context, theme)

        val layoutRes = when {
            theme == THEME_TRANSPARENT -> R.layout.widget_speed_stats_transparent
            isLight -> R.layout.widget_speed_stats_light
            else -> R.layout.widget_speed_stats
        }

        val views = RemoteViews(context.packageName, layoutRes)

        // Adattamento altezza
        val widgetMinH = try {
            manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        } catch (_: Throwable) { 0 }
        val veryCompact = widgetMinH in 1..79
        val mediumCompact = widgetMinH in 80..119
        val progressVisible = if (veryCompact || mediumCompact) View.GONE else View.VISIBLE
        val subtitleVisible = if (veryCompact || mediumCompact) View.GONE else View.VISIBLE
        val labelVisible = if (veryCompact) View.GONE else View.VISIBLE
        val valueTextSize = when {
            veryCompact -> 12f
            mediumCompact -> 17f
            else -> 22f
        }

        // v289: background NON settato via setInt - non è @RemotableViewMethod su tutti gli Android
        // Il layout XML determina il background. Per il tema transparent forzo il layout dark via layoutRes già selezionato sopra.

        // Leggo le 3 sezioni configurate per questo widget
        val sections = readSections(context, id)

        // Renderizzo ogni colonna
        renderColumn(context, views, 1, sections[0], veryCompact, mediumCompact, valueTextSize,
                     progressVisible, subtitleVisible, labelVisible)
        renderColumn(context, views, 2, sections[1], veryCompact, mediumCompact, valueTextSize,
                     progressVisible, subtitleVisible, labelVisible)
        renderColumn(context, views, 3, sections[2], veryCompact, mediumCompact, valueTextSize,
                     progressVisible, subtitleVisible, labelVisible)

        // Refresh button
        try {
            if (!autoRefresh) {
                views.setViewVisibility(R.id.refreshBtn, View.VISIBLE)
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
        } catch (_: Throwable) {}

        // Fallback tap su root (apre launcher) - viene OVERRIDE da renderColumn se la sezione 
        // ha un tap handler. RemoteViews permette OnClickPendingIntent solo se si applica a un 
        // figlio cliccabile, quindi setto al root per coprire zone "vuote"
        try {
            val rootPi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, rootPi)
        } catch (_: Throwable) {}

        manager.updateAppWidget(id, views)
    }

    /** Renderizza una colonna del widget in base alla sezione */
    private fun renderColumn(
        context: Context, views: RemoteViews, colIdx: Int, section: String,
        veryCompact: Boolean, mediumCompact: Boolean, valueTextSize: Float,
        progressVisible: Int, subtitleVisible: Int, labelVisible: Int
    ) {
        val ids = colIds(colIdx)
        try {
            // textSize value (uguale per tutti)
            views.setTextViewTextSize(ids.value, android.util.TypedValue.COMPLEX_UNIT_SP, valueTextSize)

            // Subtitle/Label/Progress default visibility (sezioni che non hanno questi elementi li nasconderanno)
            views.setViewVisibility(ids.subtitle, subtitleVisible)
            views.setViewVisibility(ids.label, labelVisible)
            views.setViewVisibility(ids.progress, progressVisible)

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
            
            // Tap handler per la colonna
            val tapIntent = tapIntentForSection(context, section)
            if (tapIntent != null) {
                val pi = PendingIntent.getActivity(
                    context, 100 + colIdx, tapIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(ids.root, pi)
            }
        } catch (t: Throwable) {
            logError(context, "renderColumn col=$colIdx section=$section", t)
            try {
                views.setTextViewText(ids.value, "—")
                views.setTextViewText(ids.subtitle, "")
                views.setTextViewText(ids.label, "")
            } catch (_: Throwable) {}
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
        views.setTextViewText(ids.subtitle, context.getString(R.string.speed_widget_section_ram_subtitle, ramAvailMb.toInt()))
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_ram))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_memory)
        views.setProgressBar(ids.progress, 100, ramPct, false)
    }

    private fun renderStorage(context: Context, views: RemoteViews, ids: ColIds) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
        val availGb = availBytes / (1024 * 1024 * 1024)
        views.setTextViewText(ids.value, "$storPct%")
        views.setTextViewText(ids.subtitle, context.getString(R.string.speed_widget_section_storage_subtitle, availGb.toInt()))
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_storage))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_storage)
        views.setProgressBar(ids.progress, 100, storPct, false)
    }

    private fun renderBattery(context: Context, views: RemoteViews, ids: ColIds) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        views.setTextViewText(ids.value, "$battPct%")
        val chargeCounter = try {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        } catch (_: Throwable) { Int.MIN_VALUE }
        val subtitle = if (chargeCounter > 0 && chargeCounter != Int.MIN_VALUE) {
            context.getString(R.string.speed_widget_section_battery_subtitle, chargeCounter / 1000)
        } else ""
        views.setTextViewText(ids.subtitle, subtitle)
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_battery))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_battery)
        views.setProgressBar(ids.progress, 100, battPct, false)
    }

    private fun renderDate(context: Context, views: RemoteViews, ids: ColIds) {
        val cal = Calendar.getInstance()
        val locale = Locale.getDefault()
        val dayShort = android.text.format.DateFormat.format("EEE", cal).toString()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        views.setTextViewText(ids.value, "$dayShort $dayNum")
        views.setTextViewText(ids.subtitle, "")
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_date))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_date)
        views.setViewVisibility(ids.progress, View.GONE)
        views.setViewVisibility(ids.subtitle, View.GONE)
    }

    private fun renderTime(context: Context, views: RemoteViews, ids: ColIds) {
        val cal = Calendar.getInstance()
        val is24 = DateFormat.is24HourFormat(context)
        val fmt = if (is24) "HH:mm" else "h:mm"
        val timeStr = android.text.format.DateFormat.format(fmt, cal).toString()
        val suffix = if (is24) "" else if (cal.get(Calendar.AM_PM) == Calendar.AM) " AM" else " PM"
        views.setTextViewText(ids.value, timeStr)
        views.setTextViewText(ids.subtitle, suffix.trim())
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_time))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_time)
        views.setViewVisibility(ids.progress, View.GONE)
        if (suffix.isEmpty()) views.setViewVisibility(ids.subtitle, View.GONE)
    }

    private fun renderWifi(context: Context, views: RemoteViews, ids: ColIds) {
        val ssid = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wm?.connectionInfo
            val raw = info?.ssid ?: ""
            raw.trim('"').takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
        } catch (_: Throwable) { null }
        val display = ssid ?: context.getString(R.string.speed_widget_no_wifi)
        // Trunco se troppo lungo
        val truncated = if (display.length > 8) display.substring(0, 7) + "…" else display
        views.setTextViewText(ids.value, truncated)
        views.setTextViewTextSize(ids.value, android.util.TypedValue.COMPLEX_UNIT_SP, 14f)  // Wi-Fi name più piccolo
        views.setTextViewText(ids.subtitle, "")
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_wifi))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_wifi)
        views.setViewVisibility(ids.progress, View.GONE)
        views.setViewVisibility(ids.subtitle, View.GONE)
    }

    private fun renderVolume(context: Context, views: RemoteViews, ids: ColIds) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val current = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        val pct = if (max > 0) ((current * 100) / max) else 0
        views.setTextViewText(ids.value, "$pct%")
        views.setTextViewText(ids.subtitle, "$current/$max")
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_volume))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_volume)
        views.setProgressBar(ids.progress, 100, pct, false)
    }

    private fun renderBrightness(context: Context, views: RemoteViews, ids: ColIds) {
        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Throwable) { 0 }
        // Range 0-255 → percent
        val pct = ((brightness * 100) / 255).coerceIn(0, 100)
        views.setTextViewText(ids.value, "$pct%")
        views.setTextViewText(ids.subtitle, "")
        views.setTextViewText(ids.label, context.getString(R.string.speed_widget_section_brightness))
        views.setImageViewResource(ids.icon, R.drawable.ic_widget_brightness)
        views.setProgressBar(ids.progress, 100, pct, false)
        views.setViewVisibility(ids.subtitle, View.GONE)
    }

    // ============= TAP HANDLERS =============

    private fun tapIntentForSection(context: Context, section: String): Intent? {
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
                else -> Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                if (parts.size == 3) {
                    arrayOf(
                        sanitizeSection(parts[0]),
                        sanitizeSection(parts[1]),
                        sanitizeSection(parts[2])
                    )
                } else DEFAULT_SECTIONS.copyOf()
            }
        } catch (_: Throwable) { DEFAULT_SECTIONS.copyOf() }
    }

    private fun sanitizeSection(s: String): String = if (s in ALL_SECTIONS) s else SECTION_RAM

    private fun readTheme(context: Context): String = try {
        val raw = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_TRANSPARENT)
        if (raw.isNullOrBlank() || raw !in setOf(THEME_SYSTEM, THEME_TRANSPARENT, THEME_LIGHT, THEME_DARK))
            THEME_TRANSPARENT else raw
    } catch (_: Throwable) { THEME_TRANSPARENT }

    private fun readAutoRefresh(context: Context): Boolean = try {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_REFRESH, true)
    } catch (_: Throwable) { true }

    private fun isLightTheme(context: Context, theme: String): Boolean = when (theme) {
        THEME_LIGHT -> true
        THEME_DARK, THEME_TRANSPARENT -> false
        else -> try {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode != Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) { false }
    }

    private fun colIds(colIdx: Int): ColIds = when (colIdx) {
        1 -> ColIds(R.id.col1_root, R.id.col1_icon, R.id.col1_value, R.id.col1_label, R.id.col1_subtitle, R.id.col1_progress)
        2 -> ColIds(R.id.col2_root, R.id.col2_icon, R.id.col2_value, R.id.col2_label, R.id.col2_subtitle, R.id.col2_progress)
        else -> ColIds(R.id.col3_root, R.id.col3_icon, R.id.col3_value, R.id.col3_label, R.id.col3_subtitle, R.id.col3_progress)
    }

    private data class ColIds(val root: Int, val icon: Int, val value: Int, val label: Int, val subtitle: Int, val progress: Int)

    private fun tryRenderFallback(context: Context, manager: AppWidgetManager, id: Int, error: Throwable) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)
            views.setTextViewText(R.id.col1_value, "—")
            views.setTextViewText(R.id.col2_value, "—")
            views.setTextViewText(R.id.col3_value, "—")
            manager.updateAppWidget(id, views)
        } catch (_: Throwable) {}
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
        const val KEY_SECTIONS_PREFIX = "widget_sections_"  // + widgetId
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

        val ALL_SECTIONS = listOf(
            SECTION_RAM, SECTION_STORAGE, SECTION_BATTERY,
            SECTION_DATE, SECTION_TIME, SECTION_WIFI,
            SECTION_VOLUME, SECTION_BRIGHTNESS
        )
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
                val joined = sections.joinToString(",")
                context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .edit().putString("$KEY_SECTIONS_PREFIX$widgetId", joined).apply()
            } catch (_: Throwable) {}
        }
    }
}
