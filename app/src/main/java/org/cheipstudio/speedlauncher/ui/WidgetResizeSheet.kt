package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v221: BottomSheet per personalizzare widget con look Material 3 Pixel.
 */
class WidgetResizeSheet : BottomSheetDialogFragment() {

    var onChanged: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val settings = SpeedApp.instance.settingsRepository

        val scroll = NestedScrollView(ctx).apply {
            isFillViewport = true
        }
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
            text = getString(R.string.widget_resize_title)
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.01f
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * d).toInt()
            lp.rightMargin = (24 * d).toInt()
            lp.bottomMargin = (8 * d).toInt()
            layoutParams = lp
        })

        // ---- POSIZIONE ----
        val positions = listOf(
            "top" to getString(R.string.widget_pos_top),
            "middle" to getString(R.string.widget_pos_middle),
            "bottom" to getString(R.string.widget_pos_bottom)
        )
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_position),
            positions.map { it.second },
            positions.indexOfFirst { it.first == (settings.widgetPosition.value ?: "top") }
        ) { idx ->
            settings.setWidgetPosition(positions[idx].first)
            onChanged?.invoke()
        }

        // ---- ALTEZZA ----
        val heights = listOf(120 to "Piccolo", 160 to "Medio", 220 to "Grande", 280 to "Extra")
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_height),
            heights.map { it.second },
            heights.indexOfFirst { it.first == (settings.widgetHeight.value ?: 160) }.coerceAtLeast(0)
        ) { idx ->
            settings.setWidgetHeight(heights[idx].first)
            onChanged?.invoke()
        }

        // ---- LARGHEZZA ----
        val widths = listOf(
            50 to getString(R.string.widget_width_50),
            75 to getString(R.string.widget_width_75),
            100 to getString(R.string.widget_width_full)
        )
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_width),
            widths.map { it.second },
            widths.indexOfFirst { it.first == (settings.widgetWidthPercent.value ?: 100) }.coerceAtLeast(0)
        ) { idx ->
            settings.setWidgetWidthPercent(widths[idx].first)
            onChanged?.invoke()
        }

        // ---- TEMA WIDGET ----
        val themes = listOf(
            "system" to getString(R.string.widget_theme_system),
            "transparent" to getString(R.string.widget_theme_transparent),
            "light" to getString(R.string.widget_theme_light),
            "dark" to getString(R.string.widget_theme_dark)
        )
        addSegmentedSection(
            root, d, getString(R.string.settings_widget_theme),
            themes.map { it.second },
            themes.indexOfFirst { it.first == (settings.widgetTheme.value ?: "system") }.coerceAtLeast(0)
        ) { idx ->
            settings.setWidgetTheme(themes[idx].first)
            try {
                org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider.refreshAll(ctx)
            } catch (_: Throwable) {}
            onChanged?.invoke()
        }

        // ---- RIMUOVI WIDGET (filled tonal error) ----
        val removeBtn = MaterialButton(ctx).apply {
            text = getString(R.string.widget_remove_action)
            cornerRadius = (32 * d).toInt()
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorErrorContainer))
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnErrorContainer))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            textSize = 15f
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
                onRemove?.invoke()
                dismiss()
            }
        }
        root.addView(removeBtn)

        return scroll
    }

    /**
     * Crea una sezione "segmented control" stile Pixel:
     * - Header testuale piccolo
     * - Riga di chip MaterialButton selezionabili (toggle group implicito)
     */
    private fun addSegmentedSection(
        parent: LinearLayout, d: Float, header: String,
        labels: List<String>, selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val ctx = requireContext()
        // Header
        parent.addView(TextView(ctx).apply {
            text = header
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
        })

        // Container scrollable horizontal per i chip
        val scroller = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * d).toInt()
            lp.rightMargin = (16 * d).toInt()
            layoutParams = lp
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val buttons = mutableListOf<MaterialButton>()
        labels.forEachIndexed { i, label ->
            // v222: stato selezione gestito manualmente (no isCheckable per evitare toggle auto)
            val isSelected = i == selectedIndex
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                isCheckable = false
                cornerRadius = (24 * d).toInt()
                strokeWidth = (1 * d).toInt()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                textSize = 14f
                isAllCaps = false
                minHeight = (44 * d).toInt()
                minimumHeight = (44 * d).toInt()
                insetTop = 0
                insetBottom = 0
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (44 * d).toInt()
                )
                lp.marginEnd = (8 * d).toInt()
                layoutParams = lp
                tag = isSelected  // memorizza stato in tag
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

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
