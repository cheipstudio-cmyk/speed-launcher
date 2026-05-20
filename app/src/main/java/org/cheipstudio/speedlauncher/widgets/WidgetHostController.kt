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

    private val prefs = activity.getSharedPreferences("speed_widget_host", android.content.Context.MODE_PRIVATE)

    /** v74: lastWidgetId persistito in SharedPreferences per sopravvivere agli update dell\'app */
    var lastWidgetId: Int = prefs.getInt(KEY_LAST_WIDGET_ID, -1)
        private set

    /** Chi ha avviato il flusso bind/configure può aspettarsi una callback */
    var pendingPlaceCallback: ((Boolean) -> Unit)? = null
    var pendingBindWidget: AppWidgetProviderInfo? = null
    var pendingBindAppWidgetId: Int = -1

    fun start() { try { host.startListening() } catch (_: Throwable) {} }

    /**
     * v74: restore widget dopo update app.
     * Chiamare al boot: ritorna AppWidgetHostView pronto da appendere alla UI, o null se il widget
     * non è più valido (provider rimosso, id non bindato, ecc).
     */
    fun restoreWidget(): AppWidgetHostView? {
        val id = lastWidgetId
        if (id < 0) return null
        return try {
            val info = appWidgetManager.getAppWidgetInfo(id) ?: return null
            host.createView(activity, id, info)
        } catch (_: Throwable) { null }
    }
    fun startListening() { try { host.startListening() } catch (_: Throwable) {} }
    fun stopListening() { try { host.stopListening() } catch (_: Throwable) {} }
    fun createView(id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(activity, id, info)

    fun markLastWidget(id: Int) {
        lastWidgetId = id
        prefs.edit().putInt(KEY_LAST_WIDGET_ID, id).apply()
    }

    fun deleteWidget(appWidgetId: Int) {
        if (appWidgetId < 0) return
        try { host.deleteAppWidgetId(appWidgetId) } catch (_: Throwable) {}
        if (appWidgetId == lastWidgetId) {
            lastWidgetId = -1
            prefs.edit().putInt(KEY_LAST_WIDGET_ID, -1).apply()
        }
    }

    /** Vecchio flusso ACTION_APPWIDGET_PICK - mantengo per compatibilità ma non usato dalla v12 */
    fun pickAndAddWidget(onPicked: (Boolean) -> Unit) {
        pendingPlaceCallback = onPicked
        val appWidgetId = host.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        activity.startActivityForResult(pickIntent, REQ_PICK)
    }


    private fun showError(msgRes: Int) {
        try {
            android.widget.Toast.makeText(activity, msgRes, android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {}
    }
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_BIND -> {
                if (resultCode == Activity.RESULT_OK) {
                    // v311: segno che l'utente ha già concesso il permesso, prossime volte salta dialog
                    try {
                        activity.getSharedPreferences("widget_bind", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("always_allowed", true).apply()
                    } catch (_: Throwable) {}
                    val info = pendingBindWidget
                    val id = pendingBindAppWidgetId
                    if (info != null && id >= 0) {
                        if (info.configure != null) {
                            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                component = info.configure
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            }
                            try {
                                activity.startActivityForResult(configIntent, REQ_CONFIGURE)
                            } catch (_: Throwable) {
                                // configure activity inacessibile → fallback: place direttamente
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                    { placeWidget(id, info) }, 100L
                                )
                            }
                        } else {
                            // v309: piccolo delay per dare tempo al bind di registrarsi
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                { placeWidget(id, info) }, 100L
                            )
                        }
                    }
                } else {
                    if (pendingBindAppWidgetId >= 0) host.deleteAppWidgetId(pendingBindAppWidgetId)
                    pendingPlaceCallback?.invoke(false)
                    pendingPlaceCallback = null
                    pendingBindWidget = null
                    pendingBindAppWidgetId = -1
                    showError(org.cheipstudio.speedlauncher.R.string.widget_bind_failed)
                }
            }
            REQ_CONFIGURE -> {
                // v210: data può essere null da molte app di config (es. Spotify/Calendar) ma il widget è valido
                // Usa pendingBindAppWidgetId che abbiamo salvato prima di lanciare config
                if (resultCode == Activity.RESULT_OK) {
                    val id = data?.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    ) ?: pendingBindAppWidgetId
                    val resolvedId = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) pendingBindAppWidgetId else id
                    val info = if (resolvedId >= 0) appWidgetManager.getAppWidgetInfo(resolvedId) else null
                    if (resolvedId >= 0 && info != null) {
                        placeWidget(resolvedId, info)
                    } else {
                        if (pendingBindAppWidgetId >= 0) host.deleteAppWidgetId(pendingBindAppWidgetId)
                        pendingPlaceCallback?.invoke(false)
                        pendingPlaceCallback = null
                    }
                } else {
                    if (pendingBindAppWidgetId >= 0) host.deleteAppWidgetId(pendingBindAppWidgetId)
                    pendingPlaceCallback?.invoke(false)
                    pendingPlaceCallback = null
                    showError(org.cheipstudio.speedlauncher.R.string.widget_config_cancelled)
                }
                pendingBindAppWidgetId = -1
                pendingBindWidget = null
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
                        pendingPlaceCallback?.invoke(false)
                        pendingPlaceCallback = null
                    }
                } else {
                    pendingPlaceCallback?.invoke(false)
                    pendingPlaceCallback = null
                }
            }
        }
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo) {
        // v289: non creo la view qui - WidgetContainerView.mountWidget la crea con createView
        // Creare due AppWidgetHostView per lo stesso id può rompere il binding
        // v306: chiamato con success=true → addWidget. Failure path chiama con false.
        // v312: forza host.startListening prima del mount per evitare "Aggiunta widget non riuscita"
        try { host.startListening() } catch (_: Throwable) {}
        // v314: invia broadcast update esplicito al provider, indipendente dal flow di mount.
        // Anche se onUpdate non parte auto dopo bind, questo lo forza.
        try {
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = info.provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            }
            activity.sendBroadcast(updateIntent)
        } catch (_: Throwable) {}
        lastWidgetId = id
        prefs.edit().putInt(KEY_LAST_WIDGET_ID, id).apply()
        pendingPlaceCallback?.invoke(true)
        pendingPlaceCallback = null
        pendingBindWidget = null
        pendingBindAppWidgetId = -1
    }

    companion object {
        const val HOST_ID = 0x53504544
        private const val KEY_LAST_WIDGET_ID = "last_widget_id"
        const val REQ_PICK = 1001
        const val REQ_CONFIGURE = 1002
        const val REQ_BIND = 1003
    }
}
