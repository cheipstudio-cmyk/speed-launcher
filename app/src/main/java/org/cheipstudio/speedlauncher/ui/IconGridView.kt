package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
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
    private var pinnedKeys: MutableList<String?> = MutableList(COLS * ROWS) { null }
    private var initialized = false

    init {
        columnCount = COLS
        rowCount = ROWS
        useDefaultMargins = false
    }

    fun setLayout(items: List<HomeItem>) {
        pinnedKeys = MutableList(COLS * ROWS) { null }
        for (item in items) {
            val idx = item.cellY * COLS + item.cellX
            if (idx in 0 until COLS * ROWS) pinnedKeys[idx] = item.key
        }
        initialized = items.isNotEmpty()
        rebuild()
    }

    fun refresh(apps: List<AppInfo>) {
        allApps = apps
        if (!initialized && apps.size > 5) {
            val toAdd = apps.drop(5).take(8)
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
            items.add(HomeItem(key = key, page = 0, cellX = i % COLS, cellY = i / COLS))
        }
        store.save(items)
    }

    private fun rebuild() {
        removeAllViews()
        if (allApps.isEmpty()) return
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until COLS * ROWS) {
            val cell: View = pinnedKeys[i]?.let { byKey[it] }?.let { app ->
                IconCellView(context).apply {
                    bind(app)
                    onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                    onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                }
            } ?: View(context)
            addView(cell, buildLayoutParams(i))
        }
    }

    private fun buildLayoutParams(index: Int): LayoutParams {
        val row = index / COLS
        val col = index % COLS
        return LayoutParams(spec(row, 1f), spec(col, 1f)).apply {
            width = 0
            height = 0
        }
    }

    companion object {
        const val COLS = 4
        const val ROWS = 4
    }
}
