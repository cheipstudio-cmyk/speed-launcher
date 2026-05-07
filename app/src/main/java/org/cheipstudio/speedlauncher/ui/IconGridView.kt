package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.View
import android.widget.GridLayout
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.HomeLayoutStore

class IconGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    var onAppLaunch: ((AppInfo, View) -> Unit)? = null
    var onAppLongPress: ((AppInfo, View) -> Unit)? = null

    var pageIndex: Int = 0

    private val store = HomeLayoutStore(context)
    private var allApps: List<AppInfo> = emptyList()
    private var pinnedKeys: MutableList<String?>
    private var initialized = false

    private var cols: Int
    private var rows: Int

    init {
        val settings = SpeedApp.instance.settingsRepository
        cols = settings.gridCols.value ?: 4
        rows = settings.gridRows.value ?: 4
        pinnedKeys = MutableList(cols * rows) { null }
        columnCount = cols
        rowCount = rows
        useDefaultMargins = false
        isClickable = false
        isFocusable = false

        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(180)
        }

        setOnDragListener { _, event -> handleDrag(event) }
    }

    fun applyGridSize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val oldKeys = pinnedKeys.filterNotNull()
        cols = newCols; rows = newRows
        columnCount = cols; rowCount = rows
        pinnedKeys = MutableList(cols * rows) { null }
        for ((i, key) in oldKeys.withIndex()) {
            if (i < cols * rows) pinnedKeys[i] = key
        }
        persist(); rebuild()
    }

    fun setLayout(items: List<HomeItem>) {
        pinnedKeys = MutableList(cols * rows) { null }
        for (item in items) {
            val idx = item.cellY * cols + item.cellX
            if (idx in 0 until cols * rows) pinnedKeys[idx] = item.key
        }
        initialized = items.isNotEmpty()
        rebuild()
    }

    fun refresh(apps: List<AppInfo>) {
        allApps = apps
        if (!initialized && pageIndex == 0 && apps.size > 5) {
            val toAdd = apps.drop(5).take(cols * rows)
            for (i in toAdd.indices) pinnedKeys[i] = toAdd[i].key
            initialized = true
            persist()
        }
        rebuild()
    }

    fun pinApp(app: AppInfo): Boolean {
        if (pinnedKeys.contains(app.key)) return true
        val emptyIdx = pinnedKeys.indexOfFirst { it == null }
        if (emptyIdx == -1) return false
        pinnedKeys[emptyIdx] = app.key
        persist(); rebuild()
        return true
    }

    fun unpinApp(app: AppInfo) {
        val idx = pinnedKeys.indexOf(app.key)
        if (idx == -1) return
        pinnedKeys[idx] = null
        persist(); rebuild()
    }

    fun isPinned(app: AppInfo): Boolean = pinnedKeys.contains(app.key)
    fun isFull(): Boolean = pinnedKeys.all { it != null }

    fun swapWith(key: String, targetIdx: Int) {
        if (targetIdx !in 0 until cols * rows) return
        val sourceIdx = pinnedKeys.indexOf(key)
        if (sourceIdx == -1) {
            // Drop da pagina diversa
            if (pinnedKeys[targetIdx] == null) {
                pinnedKeys[targetIdx] = key
            } else {
                val emptyIdx = pinnedKeys.indexOfFirst { it == null }
                if (emptyIdx != -1) pinnedKeys[emptyIdx] = key
            }
            persist(); rebuild()
            return
        }
        if (sourceIdx == targetIdx) return
        val tmp = pinnedKeys[targetIdx]
        pinnedKeys[targetIdx] = key
        pinnedKeys[sourceIdx] = tmp
        persist(); rebuild()
    }

    private fun persist() {
        val items = mutableListOf<HomeItem>()
        for (i in pinnedKeys.indices) {
            val key = pinnedKeys[i] ?: continue
            items.add(HomeItem(key = key, page = pageIndex, cellX = i % cols, cellY = i / cols))
        }
        store.savePage(pageIndex, items)
    }

    private fun rebuild() {
        removeAllViews()
        if (allApps.isEmpty()) {
            for (i in 0 until cols * rows) addView(emptyCell(i), buildLayoutParams(i))
            return
        }
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until cols * rows) {
            val cell: View = pinnedKeys[i]?.let { byKey[it] }?.let { app ->
                IconCellView(context).apply {
                    bind(app)
                    dragOriginId = "grid${pageIndex}:$i"
                    onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                    onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                }
            } ?: emptyCell(i)
            addView(cell, buildLayoutParams(i))
        }
    }

    private fun emptyCell(index: Int): View = View(context).apply {
        isClickable = false
        isFocusable = false
        isLongClickable = false
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
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_EXITED -> true
            DragEvent.ACTION_DRAG_LOCATION -> true
            DragEvent.ACTION_DROP -> handleDrop(event)
            DragEvent.ACTION_DRAG_ENDED -> true
            else -> false
        }
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
