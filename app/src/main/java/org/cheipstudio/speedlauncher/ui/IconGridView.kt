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
 * v16: griglia che supporta app E folder.
 * Drop su una cella vuota = sposta. Drop su un'altra app = crea folder. Drop su folder = aggiungi.
 */
class IconGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    var onAppLaunch: ((AppInfo, View) -> Unit)? = null
    var onAppLongPress: ((AppInfo, View) -> Unit)? = null
    var onFolderOpen: ((HomeItem) -> Unit)? = null
    var pageIndex: Int = 0

    private val store = HomeLayoutStore(context)
    private var allApps: List<AppInfo> = emptyList()
    /** v16: ora memorizziamo HomeItem? invece di String? per tenere folder + apps */
    private var pinnedItems: MutableList<HomeItem?>
    private var initialized = false
    private var cols: Int
    private var rows: Int

    private val edgeHandler = Handler(Looper.getMainLooper())
    private var pendingEdgeTarget = -1

    init {
        val settings = SpeedApp.instance.settingsRepository
        cols = settings.gridCols.value ?: 4
        rows = settings.gridRows.value ?: 4
        pinnedItems = MutableList(cols * rows) { null }
        columnCount = cols; rowCount = rows
        useDefaultMargins = false
        isClickable = false; isFocusable = false
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(160)
        }
        setOnDragListener { _, event -> handleDrag(event) }
    }

    fun applyGridSize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val old = pinnedItems.filterNotNull()
        cols = newCols; rows = newRows
        columnCount = cols; rowCount = rows
        pinnedItems = MutableList(cols * rows) { null }
        for ((i, item) in old.withIndex()) if (i < cols * rows) {
            pinnedItems[i] = item.copy(cellX = i % cols, cellY = i / cols)
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

    fun refresh(apps: List<AppInfo>) {
        allApps = apps
        if (!initialized && pageIndex == 0 && apps.size > 5) {
            val toAdd = apps.drop(5).take(cols * rows)
            for (i in toAdd.indices) {
                pinnedItems[i] = HomeItem(
                    key = toAdd[i].key, page = pageIndex,
                    cellX = i % cols, cellY = i / cols, type = HomeItem.TYPE_APP
                )
            }
            initialized = true
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

    /** Ritorna tutti gli items (per persistenza globale) */
    fun getItems(): List<HomeItem> = pinnedItems.filterNotNull().mapIndexed { i, item ->
        item.copy(cellX = i % cols, cellY = i / cols, page = pageIndex)
    }

    /**
     * v16: Sposta un item esistente (app o folder) in una cella.
     * Se la cella ha già un'app → crea folder.
     * Se la cella ha già una folder → aggiungi app alla folder.
     */
    fun handleIncomingDrop(itemKey: String, fromGrid: IconGridView?, fromIdx: Int, targetIdx: Int) {
        if (targetIdx !in 0 until cols * rows) return

        // Trova l'item sorgente
        val sourceItem: HomeItem? = if (fromGrid == this && fromIdx in pinnedItems.indices) {
            pinnedItems[fromIdx]
        } else {
            // app dal drawer: cerca per key
            val app = allApps.find { it.key == itemKey }
            if (app != null) HomeItem(
                key = app.key, page = pageIndex,
                cellX = 0, cellY = 0, type = HomeItem.TYPE_APP
            ) else null
        }
        if (sourceItem == null) return

        val targetItem = pinnedItems[targetIdx]

        when {
            // cella vuota: muovi
            targetItem == null -> {
                pinnedItems[targetIdx] = sourceItem.copy(page = pageIndex,
                    cellX = targetIdx % cols, cellY = targetIdx / cols)
                if (fromGrid == this && fromIdx in pinnedItems.indices && fromIdx != targetIdx) {
                    pinnedItems[fromIdx] = null
                }
            }
            // target è folder: aggiungi sourceItem se è un'app
            targetItem.type == HomeItem.TYPE_FOLDER && sourceItem.type == HomeItem.TYPE_APP -> {
                if (!targetItem.folderApps.contains(sourceItem.key)) {
                    pinnedItems[targetIdx] = targetItem.copy(
                        folderApps = targetItem.folderApps + sourceItem.key
                    )
                }
                if (fromGrid == this && fromIdx in pinnedItems.indices) {
                    pinnedItems[fromIdx] = null
                }
            }
            // target è app, source è app, source != target: crea folder
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
                if (fromGrid == this && fromIdx in pinnedItems.indices) {
                    pinnedItems[fromIdx] = null
                }
            }
            // target è folder ma source è folder o stesso elemento: swap normale
            else -> {
                if (sourceItem.key == targetItem.key) return
                if (fromGrid == this && fromIdx in pinnedItems.indices && fromIdx != targetIdx) {
                    pinnedItems[targetIdx] = sourceItem.copy(page = pageIndex,
                        cellX = targetIdx % cols, cellY = targetIdx / cols)
                    pinnedItems[fromIdx] = targetItem.copy(
                        cellX = fromIdx % cols, cellY = fromIdx / cols
                    )
                }
            }
        }
        persist(); rebuild()
        // segnale per controllare se la pagina sorgente è vuota
        fromGrid?.persistAndRebuild()
    }

    fun persistAndRebuild() {
        persist(); rebuild()
    }

    fun updateFolder(folderKey: String, transform: (HomeItem) -> HomeItem?) {
        val idx = pinnedItems.indexOfFirst { it?.type == HomeItem.TYPE_FOLDER && it.key == folderKey }
        if (idx == -1) return
        val current = pinnedItems[idx] ?: return
        val updated = transform(current)
        pinnedItems[idx] = updated  // null = elimina
        persist(); rebuild()
    }

    fun findFolder(folderKey: String): HomeItem? {
        return pinnedItems.firstOrNull { it?.type == HomeItem.TYPE_FOLDER && it.key == folderKey }
    }

    private fun persist() {
        store.savePage(pageIndex, getItems())
    }

    private fun rebuild() {
        removeAllViews()
        if (allApps.isEmpty()) {
            for (i in 0 until cols * rows) addView(emptyCell(i), buildLayoutParams(i))
            return
        }
        val byKey = allApps.associateBy { it.key }
        val gridSelf = this
        for (i in 0 until cols * rows) {
            val item = pinnedItems[i]
            val cell: View = when {
                item == null -> emptyCell(i)
                item.type == HomeItem.TYPE_FOLDER -> FolderCellView(context).apply {
                    bind(item)
                    dragOriginId = "grid${pageIndex}:$i"
                    onOpen = { f -> onFolderOpen?.invoke(f) }
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
        val edgeZone = width * 0.18f
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
        }, 350L)
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
