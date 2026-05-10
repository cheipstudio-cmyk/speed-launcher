package org.cheipstudio.speedlauncher.data

/**
 * v240: Widget pinnato in home con modello celle-based (Launcher3 pattern).
 *
 * La griglia widget ha cols × rows celle (es. 4x4). Un widget occupa spanX × spanY celle
 * a partire da (cellX, cellY).
 *
 * Per ora la "griglia widget" è separata dalla griglia icone: ogni pagina ha
 * un'area widget in alto + griglia icone sotto. In futuro le due si unificheranno.
 */
data class WidgetItem(
    val uuid: String,           // identificatore univoco persistente
    val appWidgetId: Int,       // ID assegnato da AppWidgetHost
    val pageIndex: Int,         // pagina home dove è posizionato
    val cellX: Int = 0,         // posizione X nella griglia widget (0..cols-1)
    val cellY: Int = 0,         // posizione Y nella griglia widget (0..rows-1)
    val spanX: Int = 4,         // celle occupate orizzontalmente (default tutta riga)
    val spanY: Int = 2          // celle occupate verticalmente (default 2 righe)
) {
    companion object {
        // Griglia widget: 4 colonne, 4 righe (approx 1/3 della home in altezza)
        const val GRID_COLS = 4
        const val GRID_ROWS = 4
        const val MIN_SPAN = 1
    }
}
