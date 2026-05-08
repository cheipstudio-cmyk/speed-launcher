package org.cheipstudio.speedlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.StatFs
import android.app.ActivityManager
import android.os.Environment
import android.widget.RemoteViews
import org.cheipstudio.speedlauncher.MainActivity
import org.cheipstudio.speedlauncher.R

/**
 * v44: Widget Speed Stats 2x4.
 * Mostra: RAM disponibile %, Storage disponibile %, Batteria %.
 * Update automatico ogni 30 minuti (Android limite minimo per widget standard).
 * Tap → apre Speed Launcher (MainActivity).
 */
class SpeedStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh esplicito (es. da broadcast esterno o tap)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpeedStatsWidgetProvider::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }
    }

    private fun updateWidget(
        context: Context, manager: AppWidgetManager, id: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_speed_stats)

        // RAM
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val ramAvailMb = mi.availMem / (1024 * 1024)
        val ramTotMb = mi.totalMem / (1024 * 1024)
        val ramPct = if (ramTotMb > 0) ((ramAvailMb * 100) / ramTotMb).toInt() else 0
        views.setTextViewText(R.id.ramPct, "$ramPct%")
        views.setTextViewText(R.id.ramSubtitle, "${ramAvailMb} MB")
        views.setProgressBar(R.id.ramProgress, 100, ramPct, false)

        // Storage interno
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val storPct = if (totalBytes > 0) ((availBytes * 100) / totalBytes).toInt() else 0
        val availGb = availBytes / (1024 * 1024 * 1024)
        views.setTextViewText(R.id.storPct, "$storPct%")
        views.setTextViewText(R.id.storSubtitle, "${availGb} GB")
        views.setProgressBar(R.id.storProgress, 100, storPct, false)

        // Batteria
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        views.setTextViewText(R.id.battPct, "$battPct%")
        // Stato carica
        val charging = bm.isCharging
        views.setTextViewText(R.id.battSubtitle,
            if (charging) context.getString(R.string.widget_charging)
            else context.getString(R.string.widget_battery))
        views.setProgressBar(R.id.battProgress, 100, battPct, false)

        // Tap → apre launcher
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pi)

        manager.updateAppWidget(id, views)
    }

    companion object {
        const val ACTION_REFRESH = "org.cheipstudio.speedlauncher.WIDGET_REFRESH"
    }
}
