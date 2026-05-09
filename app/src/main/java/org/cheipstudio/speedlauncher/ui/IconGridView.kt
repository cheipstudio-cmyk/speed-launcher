package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.HomeLayoutStore

/**
 * v19: fix drag tra pagine + drag in folder.
 * - handleIncomingDrop ora rimuove SEMPRE dall'origine, anche se fromGrid != this
 * - rebuild senza LayoutTransition durante l'operazione (più veloce)
 */
class IconGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    var onAppLaunch: ((AppInfo, View) -> Unit)? = null
    /** v59: tap su memory cleaner button */
    var onMemoryCleanerTap: ((android.view.View) -> Unit)? = null
    var onAppLongPress: ((AppInfo, View) -> Unit)? = null
    var onFolderOpen: ((HomeItem) -> Unit)? = null
    /** v132: long press cartella → menu rinomina/elimina */
    var onFolderLongPress: ((HomeItem) -> Unit)? = null
    var pageIndex: Int = 0

    private val store = HomeLayoutStore(context)
    private var allApps: List<AppInfo> = emptyList()
    private var pinnedItems: MutableList<HomeItem?>
    private var initialized = false
    private var cols: Int
    private var rows: Int

    private val edgeHandler = Handler(Looper.getMainLooper())
    private var pendingEdgeTarget = -1

    init {
        val settings = SpeedApp.instance.settingsRepository
        // v34: in landscape scambia cols<->rows per usare lo schermo orizzontalmente
        val isLandscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val savedCols = settings.gridCols.value ?: 4
        val savedRows = settings.gridRows.value ?: 4
        if (isLandscape) {
            // v198: in landscape usa più colonne per riempire la larghezza dello schermo
            // Aspect ratio dello schermo determina cols ottimali
            val dm = context.resources.displayMetrics
            val ratio = dm.widthPixels.toFloat() / dm.heightPixels.toFloat().coerceAtLeast(1f)
            val targetCols = when {
                ratio >= 2.1f -> savedCols + 3  // ultrawide / foldable
                ratio >= 1.7f -> savedCols + 2  // 16:9 standard
                else -> savedCols + 1
            }.coerceIn(savedRows, 8)
            cols = targetCols
            rows = savedRows.coerceAtMost(savedCols)
        } else {
            cols = savedCols
            rows = savedRows
        }
        pinnedItems = MutableList(cols * rows) { null }
        columnCount = cols; rowCount = rows
        useDefaultMargins = false
        isClickable = false; isFocusable = false
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(70)
        }
        setOnDragListener { _, event -> handleDrag(event) }
    }

    fun applyGridSize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        // v100: salvo gli items con la loro posizione originale (cellX/cellY)
        val oldItems = pinnedItems.toList()
        val oldCols = cols
        cols = newCols; rows = newRows
        columnCount = cols; rowCount = rows
        pinnedItems = MutableList(cols * rows) { null }

        // v100: tento di preservare le posizioni originali quando possibile
        // Solo se l\'item entra ancora nella nuova griglia (cellX < newCols, cellY < newRows)
        val orphans = mutableListOf<HomeItem>()
        for (item in oldItems) {
            if (item == null) continue
            if (item.cellX < newCols && item.cellY < newRows) {
                val idx = item.cellY * newCols + item.cellX
                if (idx in pinnedItems.indices && pinnedItems[idx] == null) {
                    pinnedItems[idx] = item
                } else {
                    orphans.add(item)
                }
            } else {
                orphans.add(item)
            }
        }

        // Item che non entrano più nella griglia: riempio gli slot vuoti rimasti
        for (item in orphans) {
            val emptyIdx = pinnedItems.indexOf(null)
            if (emptyIdx >= 0) {
                pinnedItems[emptyIdx] = item.copy(
                    cellX = emptyIdx % newCols,
                    cellY = emptyIdx / newCols
                )
            }
            // Se non c\'è più spazio l\'item viene perso (raro: succede solo riducendo griglia molto)
        }

        persist(); rebuild()
    }

    fun setLayout(items: List<HomeItem>) {
        pinnedItems = MutableList(cols * rows) { null }
        for (item in items) {
            val idx = item.cellY * cols + item.cellX
            if (idx in 0 until cols * rows) pinnedItems[idx] = item
        }
        initialized = items.isNotEmpty()
        rebuild()
    }

    /** v61: rimuove il button memory cleaner da questa pagina (se presente) */
    fun removeMemoryCleaner(): Boolean {
        var changed = false
        for (i in pinnedItems.indices) {
            val item = pinnedItems[i]
            if (item?.type == HomeItem.TYPE_TOOL && item.key == HomeItem.TOOL_MEMORY_CLEANER) {
                pinnedItems[i] = null
                changed = true
            }
        }
        if (changed) { persist(); rebuild() }
        return changed
    }

    /** v61: aggiunge il button memory cleaner nella prima cella libera (se non già presente) */
    fun addMemoryCleanerIfMissing(): Boolean {
        // Già presente?
        if (pinnedItems.any { it?.type == HomeItem.TYPE_TOOL && it.key == HomeItem.TOOL_MEMORY_CLEANER }) {
            return false
        }
        val emptyIdx = pinnedItems.indexOfFirst { it == null }
        if (emptyIdx < 0) return false
        pinnedItems[emptyIdx] = HomeItem(
            key = HomeItem.TOOL_MEMORY_CLEANER,
            page = pageIndex,
            cellX = emptyIdx % cols, cellY = emptyIdx / cols,
            type = HomeItem.TYPE_TOOL
        )
        persist(); rebuild()
        return true
    }

    fun refresh(apps: List<AppInfo>) {
        allApps = apps
        val settings = SpeedApp.instance.settingsRepository
        // v55: prefill se non l\'abbiamo MAI fatto (flag separato da firstRunDone) AND home vuota
        // Usa SharedPreferences direttamente perché è un flag one-shot
        val prefillPrefs = context.getSharedPreferences("speed_prefill", android.content.Context.MODE_PRIVATE)
        val alreadyPrefilled = prefillPrefs.getBoolean("default_apps_prefilled", false)
        if (!alreadyPrefilled && pageIndex == 0 && pinnedItems.all { it == null } && apps.isNotEmpty()) {
            // Whitelist app comuni in ordine di preferenza
            val preferred = listOf(
                "com.google.android.dialer",          // Telefono
                "com.android.dialer",
                "com.android.contacts",                // Contatti
                "com.google.android.contacts",
                "com.google.android.apps.messaging",  // Messaggi
                "com.android.mms",
                "com.google.android.gm",              // Gmail
                "com.google.android.youtube",         // YouTube
                "com.google.android.apps.maps",       // Maps
                "com.android.chrome",                  // Chrome
                "com.google.android.chrome",
                "com.whatsapp",                        // WhatsApp
                "com.instagram.android",               // Instagram
                "com.spotify.music",                   // Spotify
                "com.android.camera",                  // Camera
                "com.android.camera2",
                "com.google.android.GoogleCamera"
            )
            val installed = apps.associateBy { it.packageName }
            val toAdd = mutableListOf<AppInfo>()
            val seen = mutableSetOf<String>()
            for (pkg in preferred) {
                if (toAdd.size >= 5) break
                val app = installed[pkg]
                if (app != null && app.packageName !in seen) {
                    toAdd.add(app)
                    seen.add(app.packageName)
                }
            }
            // Fallback: se whitelist ne riempie meno di 5, completo con app comuni rimanenti
            if (toAdd.size < 5) {
                for (app in apps) {
                    if (toAdd.size >= 5) break
                    if (app.packageName !in seen && app.packageName != "org.cheipstudio.speedlauncher") {
                        toAdd.add(app)
                        seen.add(app.packageName)
                    }
                }
            }
            for (i in toAdd.indices) {
                pinnedItems[i] = HomeItem(
                    key = toAdd[i].key, page = pageIndex,
                    cellX = i % cols, cellY = i / cols, type = HomeItem.TYPE_APP
                )
            }
            // v59: aggiungo il memory cleaner come bottone fisso al primo run (se abilitato)
            val cleanerEnabled = settings.memoryCleanerEnabled.value == true
            if (cleanerEnabled) {
                val cleanerIdx = toAdd.size.coerceAtMost(pinnedItems.size - 1)
                if (cleanerIdx in pinnedItems.indices && pinnedItems[cleanerIdx] == null) {
                    pinnedItems[cleanerIdx] = HomeItem(
                        key = HomeItem.TOOL_MEMORY_CLEANER,
                        page = pageIndex,
                        cellX = cleanerIdx % cols, cellY = cleanerIdx / cols,
                        type = HomeItem.TYPE_TOOL
                    )
                }
            }
            initialized = true
            settings.markFirstRunDone()
            // v55: marca prefill done (separato da firstRunDone)
            prefillPrefs.edit().putBoolean("default_apps_prefilled", true).apply()
            persist()
        }
        rebuild()
    }

    fun pinApp(app: AppInfo): Boolean {
        if (pinnedItems.any { it?.type == HomeItem.TYPE_APP && it.key == app.key }) return true
        val emptyIdx = pinnedItems.indexOfFirst { it == null }
        if (emptyIdx == -1) return false
        pinnedItems[emptyIdx] = HomeItem(
            key = app.key, page = pageIndex,
            cellX = emptyIdx % cols, cellY = emptyIdx / cols, type = HomeItem.TYPE_APP
        )
        persist(); rebuild()
        return true
    }

    fun unpinApp(app: AppInfo) {
        val idx = pinnedItems.indexOfFirst { it?.type == HomeItem.TYPE_APP && it.key == app.key }
        if (idx == -1) return
        pinnedItems[idx] = null
        persist(); rebuild()
    }

    fun isPinned(app: AppInfo): Boolean =
        pinnedItems.any { it?.type == HomeItem.TYPE_APP && it.key == app.key }

    fun isFull(): Boolean = pinnedItems.all { it != null }
    fun isEmpty(): Boolean = pinnedItems.all { it == null }

    /** v132: PRESERVA POSIZIONI ORIGINALI! Prima `mapIndexed` compattava tutto in alto.
     *  Ora ogni item mantiene cellX/cellY in base allo slot che occupa nella griglia. */
    fun getItems(): List<HomeItem> {
        val result = mutableListOf<HomeItem>()
        for (idx in pinnedItems.indices) {
            val item = pinnedItems[idx] ?: continue
            result.add(item.copy(cellX = idx % cols, cellY = idx / cols, page = pageIndex))
        }
        return result
    }

    /**
     * v19: rimozione esplicita di un item per indice (usato dal drag tra pagine).
     */
    fun removeAt(index: Int) {
        if (index in pinnedItems.indices) {
            pinnedItems[index] = null
            persist(); rebuild()
        }
    }

    /**
     * v19: handleIncomingDrop ora rimuove correttamente dall'origine anche se fromGrid != this.
     */
    fun handleIncomingDrop(itemKey: String, fromGrid: IconGridView?, fromIdx: Int, targetIdx: Int) {
        if (targetIdx !in 0 until cols * rows) return

        // Trova l'item sorgente
        val sourceItem: HomeItem? = if (fromGrid != null && fromIdx in fromGrid.pinnedItems.indices) {
            fromGrid.pinnedItems[fromIdx]
        } else {
            val app = allApps.find { it.key == itemKey }
            if (app != null) HomeItem(
                key = app.key, page = pageIndex,
                cellX = 0, cellY = 0, type = HomeItem.TYPE_APP
            ) else null
        }
        if (sourceItem == null) return

        val targetItem = pinnedItems[targetIdx]
        val isCrossGrid = fromGrid != null && fromGrid !== this
        val sameCell = !isCrossGrid && fromIdx == targetIdx

        when {
            // cella vuota: muovi
            targetItem == null -> {
                pinnedItems[targetIdx] = sourceItem.copy(
                    page = pageIndex,
                    cellX = targetIdx % cols, cellY = targetIdx / cols
                )
                if (sameCell) return
                if (isCrossGrid) {
                    fromGrid?.removeAt(fromIdx)
                } else if (fromGrid === this && fromIdx in pinnedItems.indices) {
                    pinnedItems[fromIdx] = null
                }
            }
            // target è folder: aggiungi sourceItem se è un'app
            targetItem.type == HomeItem.TYPE_FOLDER && sourceItem.type == HomeItem.TYPE_APP -> {
                if (sameCell) return
                if (!targetItem.folderApps.contains(sourceItem.key)) {
                    val updated = targetItem.copy(
                        folderApps = targetItem.folderApps + sourceItem.key
                    )
                    pinnedItems[targetIdx] = updated
                    // v132: se la cartella è aperta, riaprila per refreshare le icone
                    try { FolderSheet.reopen(updated) } catch (_: Throwable) {}
                }
                if (isCrossGrid) {
                    fromGrid?.removeAt(fromIdx)
                } else if (fromGrid === this && fromIdx in pinnedItems.indices) {
                    pinnedItems[fromIdx] = null
                }
            }
            // target è app, source è app: crea folder
            targetItem.type == HomeItem.TYPE_APP && sourceItem.type == HomeItem.TYPE_APP &&
                    targetItem.key != sourceItem.key -> {
                val folderId = "f_${System.currentTimeMillis()}"
                val newFolder = HomeItem(
                    key = folderId, page = pageIndex,
                    cellX = targetIdx % cols, cellY = targetIdx / cols,
                    type = HomeItem.TYPE_FOLDER, name = "Cartella",
                    folderApps = listOf(targetItem.key, sourceItem.key)
                )
                pinnedItems[targetIdx] = newFolder
                if (isCrossGrid) {
                    fromGrid?.removeAt(fromIdx)
                } else if (fromGrid === this && fromIdx in pinnedItems.indices) {
                    pinnedItems[fromIdx] = null
                }
            }
            // swap (stessa pagina)
            else -> {
                if (sameCell) return
                if (sourceItem.key == targetItem.key) return
                if (!isCrossGrid && fromGrid === this && fromIdx in pinnedItems.indices) {
                    pinnedItems[targetIdx] = sourceItem.copy(
                        page = pageIndex,
                        cellX = targetIdx % cols, cellY = targetIdx / cols
                    )
                    pinnedItems[fromIdx] = targetItem.copy(
                        cellX = fromIdx % cols, cellY = fromIdx / cols
                    )
                } else if (isCrossGrid) {
                    // cross-page: target ha già qualcosa, fai semplice swap di pagine
                    // metti source qui, sposta target sulla pagina sorgente
                    val targetCopy = targetItem.copy()
                    pinnedItems[targetIdx] = sourceItem.copy(
                        page = pageIndex,
                        cellX = targetIdx % cols, cellY = targetIdx / cols
                    )
                    fromGrid?.replaceAt(fromIdx, targetCopy.copy(
                        page = fromGrid.pageIndex,
                        cellX = fromIdx % fromGrid.cols, cellY = fromIdx / fromGrid.cols
                    ))
                }
            }
        }
        persist(); rebuild()
    }

    /** v19: helper per cross-page swap */
    fun replaceAt(index: Int, item: HomeItem) {
        if (index in pinnedItems.indices) {
            pinnedItems[index] = item
            persist(); rebuild()
        }
    }

    fun persistAndRebuild() {
        persist(); rebuild()
    }

    fun updateFolder(folderKey: String, transform: (HomeItem) -> HomeItem?) {
        val idx = pinnedItems.indexOfFirst { it?.type == HomeItem.TYPE_FOLDER && it.key == folderKey }
        if (idx == -1) return
        val current = pinnedItems[idx] ?: return
        val updated = transform(current)
        pinnedItems[idx] = updated
        persist(); rebuild()
    }

    fun findFolder(folderKey: String): HomeItem? {
        return pinnedItems.firstOrNull { it?.type == HomeItem.TYPE_FOLDER && it.key == folderKey }
    }

    private fun persist() {
        store.savePage(pageIndex, getItems())
    }

    private fun rebuild() {
        // v19: disabilita transizioni durante rebuild → meno lag
        val transition = layoutTransition
        layoutTransition = null
        removeAllViews()
        if (allApps.isEmpty()) {
            for (i in 0 until cols * rows) addView(emptyCell(i), buildLayoutParams(i))
            layoutTransition = transition
            return
        }
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until cols * rows) {
            val item = pinnedItems[i]
            val cell: View = when {
                item == null -> emptyCell(i)
                item.type == HomeItem.TYPE_FOLDER -> FolderCellView(context).apply {
                    bind(item)
                    dragOriginId = "grid${pageIndex}:$i"
                    onOpen = { f -> onFolderOpen?.invoke(f) }
                    onLongPress = { f -> onFolderLongPress?.invoke(f) }
                }
                // v59: tool memory cleaner
                item.type == HomeItem.TYPE_TOOL && item.key == HomeItem.TOOL_MEMORY_CLEANER -> {
                    IconCellView(context).apply {
                        bindMemoryCleaner()
                        dragOriginId = "grid${pageIndex}:$i"
                        onMemoryCleaner = { onMemoryCleanerTap?.invoke(this) }
                    }
                }
                else -> {
                    val app = byKey[item.key]
                    if (app == null) emptyCell(i)
                    else IconCellView(context).apply {
                        bind(app)
                        dragOriginId = "grid${pageIndex}:$i"
                        onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                        onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                    }
                }
            }
            addView(cell, buildLayoutParams(i))
        }
        layoutTransition = transition
    }

    private fun emptyCell(index: Int): View = View(context).apply {
        isClickable = false; isFocusable = false; isLongClickable = false
        tag = "grid${pageIndex}:$index"
    }

    private fun buildLayoutParams(index: Int): LayoutParams {
        val row = index / cols
        val col = index % cols
        return LayoutParams(spec(row, 1f), spec(col, 1f)).apply { width = 0; height = 0 }
    }

    private fun handleDrag(event: DragEvent): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENTERED -> true
            DragEvent.ACTION_DRAG_LOCATION -> { checkEdgeForPageChange(event.x, event.y); true }
            DragEvent.ACTION_DRAG_EXITED -> { cancelEdgeScroll(); true }
            DragEvent.ACTION_DROP -> { cancelEdgeScroll(); handleDrop(event) }
            DragEvent.ACTION_DRAG_ENDED -> { cancelEdgeScroll(); true }
            else -> false
        }
    }

    private fun checkEdgeForPageChange(x: Float, y: Float) {
        val edgeZone = width * 0.12f  // v64: 12% — via di mezzo tra 20% (troppo larga) e 8% (troppo stretta)
        val pager = findPager() ?: return
        val newTarget = when {
            x < edgeZone && pager.currentPage > 0 -> pager.currentPage - 1
            x > width - edgeZone && pager.currentPage < pager.pageCount - 1 -> pager.currentPage + 1
            else -> -1
        }
        if (newTarget == -1) { cancelEdgeScroll(); return }
        if (pendingEdgeTarget == newTarget) return
        cancelEdgeScroll()
        pendingEdgeTarget = newTarget
        edgeHandler.postDelayed({
            if (pendingEdgeTarget == newTarget) pager.snapToPage(newTarget, animate = true)
            pendingEdgeTarget = -1
        }, 400L)  // v64: 400ms — abbastanza per drag fini, non troppo lento
    }

    private fun cancelEdgeScroll() {
        pendingEdgeTarget = -1
        edgeHandler.removeCallbacksAndMessages(null)
    }

    private fun findPager(): PagedHomeContainer? {
        var p: ViewGroup? = parent as? ViewGroup
        while (p != null) {
            if (p is PagedHomeContainer) return p
            p = p.parent as? ViewGroup
        }
        return null
    }

    private fun handleDrop(event: DragEvent): Boolean {
        val targetCellIdx = findCellAt(event.x, event.y) ?: return false
        val text = (event.clipData?.getItemAt(0)?.text ?: return false).toString()
        val parts = text.split("|")
        if (parts.size != 2) return false
        SpeedApp.instance.dragHandler?.invoke(parts[0], parts[1], "grid${pageIndex}:$targetCellIdx")
        return true
    }

    private fun findCellAt(x: Float, y: Float): Int? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) return i
        }
        return null
    }
}
