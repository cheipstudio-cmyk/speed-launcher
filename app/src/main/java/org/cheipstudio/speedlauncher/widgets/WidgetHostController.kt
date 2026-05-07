package org.cheipstudio.speedlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent

class WidgetHostController(private val activity: Activity) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host: AppWidgetHost = AppWidgetHost(activity, HOST_ID)

    var lastWidgetId: Int = -1
        private set

    /** Chi ha avviato il flusso bind/configure può aspettarsi una callback */
    var pendingPlaceCallback: ((AppWidgetHostView?) -> Unit)? = null
    var pendingBindWidget: AppWidgetProviderInfo? = null
    var pendingBindAppWidgetId: Int = -1

    fun start() { host.startListening() }
    fun startListening() { try { host.startListening() } catch (_: Throwable) {} }
    fun stopListening() { try { host.stopListening() } catch (_: Throwable) {} }
    fun createView(id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(activity, id, info)

    fun markLastWidget(id: Int) { lastWidgetId = id }

    fun deleteWidget(appWidgetId: Int) {
        if (appWidgetId < 0) return
        try { host.deleteAppWidgetId(appWidgetId) } catch (_: Throwable) {}
        if (appWidgetId == lastWidgetId) lastWidgetId = -1
    }

    /** Vecchio flusso ACTION_APPWIDGET_PICK - mantengo per compatibilità ma non usato dalla v12 */
    fun pickAndAddWidget(onPicked: (AppWidgetHostView?) -> Unit) {
        pendingPlaceCallback = onPicked
        val appWidgetId = host.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        activity.startActivityForResult(pickIntent, REQ_PICK)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_BIND -> {
                if (resultCode == Activity.RESULT_OK) {
                    val info = pendingBindWidget
                    val id = pendingBindAppWidgetId
                    if (info != null && id >= 0) {
                        if (info.configure != null) {
                            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                component = info.configure
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            }
                            activity.startActivityForResult(configIntent, REQ_CONFIGURE)
                        } else {
                            placeWidget(id, info)
                        }
                    }
                } else {
                    if (pendingBindAppWidgetId >= 0) host.deleteAppWidgetId(pendingBindAppWidgetId)
                    pendingPlaceCallback?.invoke(null)
                    pendingPlaceCallback = null
                    pendingBindWidget = null
                    pendingBindAppWidgetId = -1
                }
            }
            REQ_CONFIGURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val id = data.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    val info = appWidgetManager.getAppWidgetInfo(id)
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID && info != null) {
                        placeWidget(id, info)
                    } else {
                        pendingPlaceCallback?.invoke(null)
                        pendingPlaceCallback = null
                    }
                } else {
                    pendingPlaceCallback?.invoke(null)
                    pendingPlaceCallback = null
                }
            }
            REQ_PICK -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val id = data.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    val info = appWidgetManager.getAppWidgetInfo(id)
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID && info != null) {
                        placeWidget(id, info)
                    } else {
                        pendingPlaceCallback?.invoke(null)
                        pendingPlaceCallback = null
                    }
                } else {
                    pendingPlaceCallback?.invoke(null)
                    pendingPlaceCallback = null
                }
            }
        }
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo) {
        val view = createView(id, info)
        view.setAppWidget(id, info)
        lastWidgetId = id
        pendingPlaceCallback?.invoke(view)
        pendingPlaceCallback = null
        pendingBindWidget = null
        pendingBindAppWidgetId = -1
    }

    companion object {
        const val HOST_ID = 0x53504544
        const val REQ_PICK = 1001
        const val REQ_CONFIGURE = 1002
        const val REQ_BIND = 1003
    }
}
