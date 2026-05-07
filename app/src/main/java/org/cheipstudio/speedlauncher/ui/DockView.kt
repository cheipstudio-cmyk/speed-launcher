package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
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
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        isClickable = false
        isFocusable = false
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

    private fun persist() {
        store.saveDock(dockKeys.map { it ?: "" })
    }

    private fun rebuild() {
        removeAllViews()
        val byKey = allApps.associateBy { it.key }
        for (i in 0 until SLOTS) {
            val key = dockKeys[i]
            val cell: View = if (key.isNullOrEmpty()) {
                View(context).apply {
                    minimumHeight = (56 * resources.displayMetrics.density).toInt()
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                }
            } else {
                byKey[key]?.let { app ->
                    IconCellView(context).apply {
                        bind(app)
                        onLaunch = { a, v -> onAppLaunch?.invoke(a, v) }
                        onMenu = { a, v -> onAppLongPress?.invoke(a, v) }
                    }
                } ?: View(context).apply {
                    minimumHeight = (56 * resources.displayMetrics.density).toInt()
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                }
            }
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    companion object {
        const val SLOTS = 5
    }
}
