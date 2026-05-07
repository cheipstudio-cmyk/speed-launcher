package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
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

    private val store = HomeLayoutStore(context)
    private var allApps: List<AppInfo> = emptyList()
    private var pinnedKeys: MutableList<String?>
    private var initialized = false

    private var cols: Int
    private var rows: Int

    init {
        // Leggo dimensione griglia dalle settings
        val settings = SpeedApp.instance.settingsRepository
        cols = settings.gridCols.value ?: 4
        rows = settings.gridRows.value ?: 4
        pinnedKeys = MutableList(cols * rows) { null }
        columnCount = cols
        rowCount = rows
        useDefaultMargins = false
        // Importante: il GridLayout NON deve consumare touch da solo
        isClickable = false
        isFocusable = false
    }

    fun applyGridSize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        // Mantieni le app pinnate quando puoi
        val oldKeys = pinnedKeys.filterNotNull()
        cols = newCols
        rows = newRows
        columnCount = cols
        rowCount = rows
        pinnedKeys = MutableList(cols * rows) { null }
        for ((i, key) in oldKeys.withIndex()) {
            if (i < cols * rows) pinnedKeys[i] = key
        }
        persist()
        rebuild()
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
        if (!initialized && apps.size > 5) {
            val toAdd = apps.drop(5).take(cols * rows - 4)
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
        persist()
        rebuild()
        return true
    }

    fun unpinApp(app: AppInfo) {
        val idx = pinnedKeys.indexOf(app.key)
        if (idx == -1) return
        pinnedKeys[idx] = null
        persist()
        rebuild()
    }

    fun isPinned(app: AppInfo): Boolean = pinnedKeys.contains(app.key)

    private fun persist() {
        val items = mutableListOf<HomeItem>()
        for (i in pinnedKeys.indices) {
            val key = pinnedKeys[i] ?: continue
            items.add(HomeItem(key = key, page = 0, cellX = i % cols, cellY = i / cols))
        }
        store.save(items)
    }

    private fun rebuild() {
        removeAllViews()
        if (allApps.isEmpty()) return
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until cols * rows) {
            val cell: View = pinnedKeys[i]?.let { byKey[it] }?.let { app ->
                IconCellView(context).apply {
                    bind(app)
                    onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                    onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                }
            } ?: emptyCell()
            addView(cell, buildLayoutParams(i))
        }
    }

    /**
     * Cella vuota: NON cliccabile e NON focusabile, così il touch passa
     * attraverso al parent (HomeView) per il long-press.
     */
    private fun emptyCell(): View {
        return View(context).apply {
            isClickable = false
            isFocusable = false
            isLongClickable = false
        }
    }

    private fun buildLayoutParams(index: Int): LayoutParams {
        val row = index / cols
        val col = index % cols
        return LayoutParams(spec(row, 1f), spec(col, 1f)).apply {
            width = 0
            height = 0
        }
    }
}
