package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.data.WidgetItem
import org.cheipstudio.speedlauncher.data.WidgetStore

/**
 * v245: Modal azioni widget. Larghezza/Altezza con label umane (Piccolo/Medio/Grande/Pieno),
 * sposta tra pagine, rimuovi.
 */
class WidgetActionsSheet : BottomSheetDialogFragment() {

    var onRemove: ((String) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val uuid = arguments?.getString(ARG_UUID) ?: ""
        val pageIndex = arguments?.getInt(ARG_PAGE) ?: 0
        val store = WidgetStore(ctx)

        val item = store.loadPage(pageIndex).firstOrNull { it.uuid == uuid }
            ?: return TextView(ctx).apply { text = "" }

        val scroll = NestedScrollView(ctx).apply { isFillViewport = true }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_modal_sheet)
            setPadding(0, (8 * d).toInt(), 0, (24 * d).toInt())
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Drag handle
        root.addView(View(ctx).apply {
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * d).toInt(), (4 * d).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (8 * d).toInt()
            lp.bottomMargin = (16 * d).toInt()
            layoutParams = lp
        })

        // Title
        root.addView(TextView(ctx).apply {
            text = getString(R.string.widget_actions_title)
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * d).toInt()
            lp.rightMargin = (24 * d).toInt()
            lp.bottomMargin = (8 * d).toInt()
            layoutParams = lp
        })

        // Larghezza: spanX → 1=25%, 2=50%, 3=75%, 4=100%
        val widthOptions = listOf(
            1 to getString(R.string.widget_size_small),
            2 to getString(R.string.widget_size_medium),
            3 to getString(R.string.widget_size_large),
            4 to getString(R.string.widget_size_full)
        )
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_width),
            widthOptions.map { it.second },
            widthOptions.indexOfFirst { it.first == item.spanX }.coerceAtLeast(0)
        ) { idx ->
            updateItem(store, item.copy(spanX = widthOptions[idx].first))
        }

        // Altezza: spanY → 1=Piccolo, 2=Medio, 3=Grande, 4=Pieno
        val heightOptions = listOf(
            1 to getString(R.string.widget_size_small),
            2 to getString(R.string.widget_size_medium),
            3 to getString(R.string.widget_size_large),
            4 to getString(R.string.widget_size_full)
        )
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_height),
            heightOptions.map { it.second },
            heightOptions.indexOfFirst { it.first == item.spanY }.coerceAtLeast(0)
        ) { idx ->
            updateItem(store, item.copy(spanY = heightOptions[idx].first))
        }

        // Sposta pagina
        root.addView(makeHeader(ctx, d, getString(R.string.widget_move_label)))
        val moveRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * d).toInt()
            lp.rightMargin = (16 * d).toInt()
            layoutParams = lp
        }
        val prevBtn = makeOutlinedButton(ctx, d, getString(R.string.widget_move_prev)) {
            if (item.pageIndex > 0) {
                store.removeWidget(item.pageIndex, item.uuid)
                store.addWidget(item.copy(pageIndex = item.pageIndex - 1))
                onChanged?.invoke()
                dismiss()
            }
        }
        val nextBtn = makeOutlinedButton(ctx, d, getString(R.string.widget_move_next)) {
            store.removeWidget(item.pageIndex, item.uuid)
            store.addWidget(item.copy(pageIndex = item.pageIndex + 1))
            onChanged?.invoke()
            dismiss()
        }
        (prevBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0; weight = 1f; marginEnd = (8 * d).toInt()
        }
        (nextBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0; weight = 1f
        }
        moveRow.addView(prevBtn)
        moveRow.addView(nextBtn)
        root.addView(moveRow)

        // v246: Tema (visibile solo per Speed Stats widget)
        try {
            val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
            val info = mgr.getAppWidgetInfo(item.appWidgetId)
            val isSpeedStats = info?.provider?.className?.contains("SpeedStatsWidgetProvider") == true
            if (isSpeedStats) {
                val themePrefs = ctx.getSharedPreferences("speed_widget_prefs", Context.MODE_PRIVATE)
                val curTheme = themePrefs.getString("widget_theme", "transparent") ?: "transparent"
                val themes = listOf(
                    "system" to getString(R.string.widget_theme_system),
                    "transparent" to getString(R.string.widget_theme_transparent),
                    "light" to getString(R.string.widget_theme_light),
                    "dark" to getString(R.string.widget_theme_dark)
                )
                addSegmentedSection(
                    root, d, getString(R.string.widget_theme_label),
                    themes.map { it.second },
                    themes.indexOfFirst { it.first == curTheme }.coerceAtLeast(0)
                ) { idx ->
                    val newTheme = themes[idx].first
                    themePrefs.edit().putString("widget_theme", newTheme).apply()
                    // Forza refresh widget Speed Stats per applicare tema
                    try {
                        val intent = android.content.Intent(ctx, Class.forName("org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider"))
                        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, "org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider"))
                        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        ctx.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}

        // Rimuovi
        val removeBtn = MaterialButton(ctx).apply {
            text = getString(R.string.widget_remove_action)
            cornerRadius = (32 * d).toInt()
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorErrorContainer))
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnErrorContainer))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            textSize = 15f
            isAllCaps = false
            insetTop = 0; insetBottom = 0
            val padV = (14 * d).toInt()
            setPadding(padV, padV, padV, padV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * d).toInt()
            lp.rightMargin = (16 * d).toInt()
            lp.topMargin = (24 * d).toInt()
            layoutParams = lp
            setOnClickListener {
                onRemove?.invoke(uuid)
                dismiss()
            }
        }
        root.addView(removeBtn)

        return scroll
    }

    private fun updateItem(store: WidgetStore, updated: WidgetItem) {
        store.updateWidget(updated)
        onChanged?.invoke()
    }

    private fun makeHeader(ctx: Context, d: Float, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.04f
            isAllCaps = false
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * d).toInt()
            lp.rightMargin = (24 * d).toInt()
            lp.topMargin = (16 * d).toInt()
            lp.bottomMargin = (10 * d).toInt()
            layoutParams = lp
        }
    }

    private fun addSegmentedSection(
        parent: LinearLayout, d: Float, header: String,
        labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit
    ) {
        val ctx = requireContext()
        parent.addView(makeHeader(ctx, d, header))

        val scroller = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * d).toInt()
            lp.rightMargin = (16 * d).toInt()
            layoutParams = lp
        }
        val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = mutableListOf<MaterialButton>()
        labels.forEachIndexed { i, label ->
            val isSelected = i == selectedIndex
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                cornerRadius = (24 * d).toInt()
                strokeWidth = (1 * d).toInt()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                textSize = 14f
                isAllCaps = false
                minimumHeight = (44 * d).toInt()
                insetTop = 0; insetBottom = 0
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (44 * d).toInt()
                )
                lp.marginEnd = (8 * d).toInt()
                layoutParams = lp
                tag = isSelected
                applyChipStyle(this, isSelected)
                setOnClickListener {
                    if (tag == true) return@setOnClickListener
                    buttons.forEach {
                        it.tag = false
                        applyChipStyle(it, false)
                    }
                    tag = true
                    applyChipStyle(this, true)
                    onSelect(i)
                }
            }
            buttons.add(btn)
            chipRow.addView(btn)
        }
        scroller.addView(chipRow)
        parent.addView(scroller)
    }

    private fun applyChipStyle(btn: MaterialButton, checked: Boolean) {
        if (checked) {
            btn.setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            btn.setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
            btn.strokeColor = android.content.res.ColorStateList.valueOf(
                resolveAttr(com.google.android.material.R.attr.colorSecondaryContainer)
            )
        } else {
            btn.setBackgroundColor(0)
            btn.setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            btn.strokeColor = android.content.res.ColorStateList.valueOf(
                resolveAttr(com.google.android.material.R.attr.colorOutlineVariant)
            )
        }
    }

    private fun makeOutlinedButton(ctx: Context, d: Float, label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            cornerRadius = (24 * d).toInt()
            isAllCaps = false
            textSize = 14f
            minimumHeight = (44 * d).toInt()
            insetTop = 0; insetBottom = 0
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (44 * d).toInt()
            )
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    companion object {
        private const val ARG_UUID = "uuid"
        private const val ARG_PAGE = "pageIndex"

        fun newInstance(uuid: String, pageIndex: Int): WidgetActionsSheet {
            return WidgetActionsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_UUID, uuid)
                    putInt(ARG_PAGE, pageIndex)
                }
            }
        }
    }
}
