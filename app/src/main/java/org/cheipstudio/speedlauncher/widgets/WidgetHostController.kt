package org.cheipstudio.speedlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle

/**
 * Gestisce l'AppWidgetHost del launcher.
 * Per la v1 supportiamo widget singoli pinnati sull'home, niente resize complesso.
 */
class WidgetHostController(private val activity: Activity) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host: AppWidgetHost = AppWidgetHost(activity, HOST_ID)

    private var pendingPickCallback: ((AppWidgetHostView?) -> Unit)? = null

    fun start() {
        host.startListening()
    }

    fun startListening() {
        try { host.startListening() } catch (_: Throwable) {}
    }

    fun stopListening() {
        try { host.stopListening() } catch (_: Throwable) {}
    }

    fun createView(appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView {
        return host.createView(activity, appWidgetId, info)
    }

    /**
     * Avvia il flusso di pick + bind di un nuovo widget.
     * Risultato consegnato via [onPicked].
     */
    fun pickAndAddWidget(onPicked: (AppWidgetHostView?) -> Unit) {
        pendingPickCallback = onPicked
        val appWidgetId = host.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        activity.startActivityForResult(pickIntent, REQ_PICK)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_PICK -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    handlePick(data)
                } else {
                    val appWidgetId = data?.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        host.deleteAppWidgetId(appWidgetId)
                    }
                    pendingPickCallback?.invoke(null)
                    pendingPickCallback = null
                }
            }
            REQ_CONFIGURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    completeAdd(data)
                } else {
                    pendingPickCallback?.invoke(null)
                    pendingPickCallback = null
                }
            }
        }
    }

    private fun handlePick(data: Intent) {
        val appWidgetId = data.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            pendingPickCallback?.invoke(null)
            pendingPickCallback = null
            return
        }
        if (info.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            activity.startActivityForResult(configIntent, REQ_CONFIGURE)
        } else {
            completeAdd(data)
        }
    }

    private fun completeAdd(data: Intent) {
        val appWidgetId = data.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingPickCallback?.invoke(null)
            pendingPickCallback = null
            return
        }
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            host.deleteAppWidgetId(appWidgetId)
            pendingPickCallback?.invoke(null)
            pendingPickCallback = null
            return
        }
        val view = createView(appWidgetId, info)
        view.setAppWidget(appWidgetId, info)
        pendingPickCallback?.invoke(view)
        pendingPickCallback = null
    }

    companion object {
        const val HOST_ID = 0x53504544 // "SPED"
        const val REQ_PICK = 1001
        const val REQ_CONFIGURE = 1002
    }
}
