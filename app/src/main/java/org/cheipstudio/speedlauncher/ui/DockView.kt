package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.View
import android.widget.LinearLayout
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeLayoutStore

class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAppLaunch: ((AppInfo, View) -> Unit)? = null
    var onAppLongPress: ((AppInfo, View) -> Unit)? = null

    private val store = HomeLayoutStore(context)
    private var allApps: List<AppInfo> = emptyList()
    private var dockKeys: MutableList<String?> = MutableList(SLOTS) { null }
    private var initialized = false

    init {
        orientation = HORIZONTAL
        weightSum = SLOTS.toFloat()
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        isClickable = false
        isFocusable = false

        setOnDragListener { _, event -> handleDrag(event) }
    }

    fun setLayout(items: List<String>) {
        dockKeys = MutableList(SLOTS) { null }
        for ((i, key) in items.withIndex()) {
            if (i in 0 until SLOTS && key.isNotEmpty()) dockKeys[i] = key
        }
        initialized = items.isNotEmpty()
        rebuild()
    }

    fun refresh(apps: List<AppInfo>) {
        allApps = apps
        if (!initialized && apps.isNotEmpty()) {
            for (i in 0 until SLOTS.coerceAtMost(apps.size)) {
                dockKeys[i] = apps[i].key
            }
            initialized = true
            persist()
        }
        rebuild()
    }

    fun isPinned(app: AppInfo): Boolean = dockKeys.contains(app.key)

    fun pinApp(app: AppInfo): Boolean {
        if (dockKeys.contains(app.key)) return true
        val emptyIdx = dockKeys.indexOfFirst { it == null }
        if (emptyIdx == -1) return false
        dockKeys[emptyIdx] = app.key
        persist()
        rebuild()
        return true
    }

    fun unpinApp(app: AppInfo) {
        val idx = dockKeys.indexOf(app.key)
        if (idx == -1) return
        dockKeys[idx] = null
        persist()
        rebuild()
    }

    /** Sostituisce app a slot specifico (per drop dock-to-dock o esterno) */
    fun placeAt(slotIndex: Int, key: String) {
        if (slotIndex !in 0 until SLOTS) return
        // Rimuovi key altrove se già presente
        val existingIdx = dockKeys.indexOf(key)
        if (existingIdx != -1) dockKeys[existingIdx] = null
        dockKeys[slotIndex] = key
        persist()
        rebuild()
    }

    /** Rimuove la app a uno slot specifico */
    fun removeAt(slotIndex: Int) {
        if (slotIndex !in 0 until SLOTS) return
        dockKeys[slotIndex] = null
        persist()
        rebuild()
    }

    /** Trova lo slot di una key */
    fun slotOf(key: String): Int = dockKeys.indexOf(key)

    private fun persist() {
        store.saveDock(dockKeys.map { it ?: "" })
    }

    private fun rebuild() {
        removeAllViews()
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until SLOTS) {
            val key = dockKeys[i]
            val cell: View = if (key.isNullOrEmpty()) {
                emptyCell(i)
            } else {
                byKey[key]?.let { app ->
                    IconCellView(context).apply {
                        bind(app)
                        dragOriginId = "dock:$i"
                        onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                        onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                    }
                } ?: emptyCell(i)
            }
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun emptyCell(index: Int): View {
        return View(context).apply {
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
            isClickable = false
            isFocusable = false
            isLongClickable = false
            tag = "dock:$index"
        }
    }

    fun beginDragFor(app: AppInfo) {
        for (i in 0 until childCount) {
            val cell = getChildAt(i) as? IconCellView ?: continue
            if (cell.packageName == app.packageName) {
                cell.beginDrag()
                return
            }
        }
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
        val targetSlot = findCellAt(event.x) ?: return false
        val text = (event.clipData?.getItemAt(0)?.text ?: return false).toString()
        val parts = text.split("|")
        if (parts.size != 2) return false
        val (origin, draggedKey) = parts
        SpeedApp.instance.dragHandler?.invoke(origin, draggedKey, "dock:$targetSlot")
        return true
    }

    private fun findCellAt(x: Float): Int? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x >= child.left && x <= child.right) return i
        }
        return null
    }

    companion object {
        const val SLOTS = 5
    }
}
