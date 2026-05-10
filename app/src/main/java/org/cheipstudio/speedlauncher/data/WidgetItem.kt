package org.cheipstudio.speedlauncher.data

/**
 * v231: Widget pinnato in home.
 * Per ora 1 widget per pagina ma il modello supporta multi-widget futuro.
 */
data class WidgetItem(
    val uuid: String,           // identificatore univoco
    val appWidgetId: Int,       // ID assegnato da AppWidgetHost
    val page: Int,              // pagina home
    val heightDp: Int = 160,    // altezza pixel/2
    val widthPercent: Int = 100, // 25/50/75/100
    val verticalPos: String = "top" // "top"/"middle"/"bottom"
)
