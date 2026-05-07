package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.View
import android.widget.LinearLayout
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
        // Spaziatura tra slot dock
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        setupDragListener()
    }

    fun setLayout(items: List<String>) {
        dockKeys = MutableList(SLOTS) { null }
        for ((i, key) in items.withIndex()) {
            if (i in 0 until SLOTS) dockKeys[i] = key
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
        // Forza re-layout del parent per evitare layout fantasma
        post { requestLayout() }
    }

    private fun persist() {
        // IMPORTANTE: salviamo tutti gli SLOTS includendo i null come stringa vuota
        // così il numero di slot si mantiene
        store.saveDock(dockKeys.map { it ?: "" })
    }

    private fun rebuild() {
        removeAllViews()
        if (allApps.isEmpty()) {
            // Anche senza app, manteniamo gli slot vuoti per non collassare il layout
            for (i in 0 until SLOTS) {
                addView(emptyCell(i), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
            return
        }
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until SLOTS) {
            val key = dockKeys[i]
            val cell: View = if (key.isNullOrEmpty()) {
                emptyCell(i)
            } else {
                byKey[key]?.let { app ->
                    IconCellView(context).apply {
                        bind(app)
                        tag = i
                        onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                        onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                        onDragStart = { a, v -> startDragForCell(v, i, a) }
                    }
                } ?: emptyCell(i)
            }
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            addView(cell, lp)
        }
    }

    private fun emptyCell(index: Int): View {
        return View(context).apply {
            tag = index
            // dimensione minima per non collassare
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
        }
    }

    private fun startDragForCell(cell: View, index: Int, app: AppInfo) {
        val data = ClipData.newPlainText("dockIdx", "dock:$index")
        cell.startDragAndDrop(data, View.DragShadowBuilder(cell), app.key, 0)
    }

    private fun setupDragListener() {
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_EXITED -> true
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DROP -> handleDrop(event)
                DragEvent.ACTION_DRAG_ENDED -> true
                else -> false
            }
        }
    }

    private fun handleDrop(event: DragEvent): Boolean {
        val target = findCellAt(event.x) ?: return false
        val targetIdx = target.tag as? Int ?: return false
        val text = (event.clipData?.getItemAt(0)?.text ?: return false).toString()
        if (!text.startsWith("dock:")) return false
        val sourceIdx = text.removePrefix("dock:").toIntOrNull() ?: return false
        if (sourceIdx == targetIdx) return true
        val tmp = dockKeys[sourceIdx]
        dockKeys[sourceIdx] = dockKeys[targetIdx]
        dockKeys[targetIdx] = tmp
        persist()
        rebuild()
        return true
    }

    private fun findCellAt(x: Float): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x >= child.left && x <= child.right) return child
        }
        return null
    }

    companion object {
        const val SLOTS = 5
    }
}
