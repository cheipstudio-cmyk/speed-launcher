package org.cheipstudio.speedlauncher.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.SettingsRepository

/**
 * v212: BottomSheet Pixel Material 3 Expressive per ridimensionare/spostare il widget.
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
        val density = resources.displayMetrics.density
        val settings = SpeedApp.instance.settingsRepository

        val scroll = androidx.core.widget.NestedScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_modal_sheet)
            setPadding(0, (8 * density).toInt(), 0, (24 * density).toInt())
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Drag handle
        root.addView(View(ctx).apply {
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (8 * density).toInt()
            lp.bottomMargin = (16 * density).toInt()
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
            lp.leftMargin = (24 * density).toInt()
            lp.rightMargin = (24 * density).toInt()
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        })

        // ---- POSIZIONE WIDGET ----
        addSectionHeader(root, density, R.string.settings_widget_position)
        val posCard = makeGroupCard(ctx, density)
        val curPos = settings.widgetPosition.value ?: "top"
        val positions = listOf(
            "top" to R.string.widget_pos_top,
            "middle" to R.string.widget_pos_middle,
            "bottom" to R.string.widget_pos_bottom
        )
        positions.forEachIndexed { i, (value, labelRes) ->
            val row = makeRow(ctx, density, getString(labelRes), value == curPos) {
                settings.setWidgetPosition(value)
                onChanged?.invoke()
                rebuildAll(posCard, density, ctx, settings, R.string.widget_pos_top, positions) { it as String == settings.widgetPosition.value }
            }
            posCard.addView(row)
        }
        root.addView(posCard)

        // ---- ALTEZZA WIDGET ----
        addSectionHeader(root, density, R.string.settings_widget_height)
        val hCard = makeGroupCard(ctx, density)
        val curH = settings.widgetHeight.value ?: 160
        val heights = listOf(
            120 to "Piccolo",
            160 to "Medio",
            220 to "Grande",
            280 to "Extra"
        )
        heights.forEach { (h, label) ->
            val row = makeRow(ctx, density, label, h == curH) {
                settings.setWidgetHeight(h)
                onChanged?.invoke()
                rebuildHeights(hCard, density, ctx, settings, heights)
            }
            hCard.addView(row)
        }
        root.addView(hCard)

        // ---- LARGHEZZA WIDGET ----
        addSectionHeader(root, density, R.string.settings_widget_width)
        val wCard = makeGroupCard(ctx, density)
        val curW = settings.widgetWidthPercent.value ?: 100
        val widths = listOf(
            50 to getString(R.string.widget_width_50),
            75 to getString(R.string.widget_width_75),
            100 to getString(R.string.widget_width_full)
        )
        widths.forEach { (w, label) ->
            val row = makeRow(ctx, density, label, w == curW) {
                settings.setWidgetWidthPercent(w)
                onChanged?.invoke()
                rebuildWidths(wCard, density, ctx, settings, widths)
            }
            wCard.addView(row)
        }
        root.addView(wCard)

        // ---- RIMUOVI WIDGET (filled tonal error) ----
        val removeBtn = MaterialButton(ctx).apply {
            text = getString(R.string.widget_remove_action)
            cornerRadius = (32 * density).toInt()
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorErrorContainer))
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnErrorContainer))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            textSize = 15f
            val padV = (14 * density).toInt()
            setPadding(padV, padV, padV, padV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * density).toInt()
            lp.rightMargin = (16 * density).toInt()
            lp.topMargin = (24 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                onRemove?.invoke()
                dismiss()
            }
        }
        root.addView(removeBtn)

        return scroll
    }

    private fun addSectionHeader(parent: LinearLayout, density: Float, labelRes: Int) {
        parent.addView(TextView(requireContext()).apply {
            setText(labelRes)
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.05f
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * density).toInt()
            lp.rightMargin = (24 * density).toInt()
            lp.topMargin = (16 * density).toInt()
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
        })
    }

    private fun makeGroupCard(ctx: android.content.Context, density: Float): LinearLayout {
        // Approccio: linearLayout con bg drawable rounded
        val ll = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28 * density
                setColor(resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * density).toInt()
            lp.rightMargin = (16 * density).toInt()
            layoutParams = lp
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }
        return ll
    }

    private fun makeRow(
        ctx: android.content.Context,
        density: Float,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            val tvSel = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
            setBackgroundResource(tvSel.resourceId)
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            minimumHeight = (52 * density).toInt()
            setOnClickListener { onClick() }
        }
        // Radio circle
        row.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                if (selected) {
                    setColor(resolveAttr(com.google.android.material.R.attr.colorPrimary))
                    setStroke((2 * density).toInt(), resolveAttr(com.google.android.material.R.attr.colorPrimary))
                } else {
                    setColor(0)
                    setStroke((2 * density).toInt(), resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                }
            }
            val s = (20 * density).toInt()
            val lp = LinearLayout.LayoutParams(s, s)
            lp.marginEnd = (16 * density).toInt()
            layoutParams = lp
        })
        // Inner dot if selected
        if (selected) {
            val frame = (row.getChildAt(0) as View)
            // Inner dot done with another view stack
        }
        // Label
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
        })
        return row
    }

    private fun rebuildAll(
        card: LinearLayout, density: Float, ctx: android.content.Context,
        settings: SettingsRepository, headerRes: Int,
        positions: List<Pair<String, Int>>, isSelected: (Any) -> Boolean
    ) {
        card.removeAllViews()
        positions.forEach { (value, labelRes) ->
            val row = makeRow(ctx, density, getString(labelRes), value == settings.widgetPosition.value) {
                settings.setWidgetPosition(value)
                onChanged?.invoke()
                rebuildAll(card, density, ctx, settings, headerRes, positions, isSelected)
            }
            card.addView(row)
        }
    }

    private fun rebuildHeights(card: LinearLayout, density: Float, ctx: android.content.Context, settings: SettingsRepository, heights: List<Pair<Int, String>>) {
        card.removeAllViews()
        val cur = settings.widgetHeight.value ?: 160
        heights.forEach { (h, label) ->
            val row = makeRow(ctx, density, label, h == cur) {
                settings.setWidgetHeight(h)
                onChanged?.invoke()
                rebuildHeights(card, density, ctx, settings, heights)
            }
            card.addView(row)
        }
    }

    private fun rebuildWidths(card: LinearLayout, density: Float, ctx: android.content.Context, settings: SettingsRepository, widths: List<Pair<Int, String>>) {
        card.removeAllViews()
        val cur = settings.widgetWidthPercent.value ?: 100
        widths.forEach { (w, label) ->
            val row = makeRow(ctx, density, label, w == cur) {
                settings.setWidgetWidthPercent(w)
                onChanged?.invoke()
                rebuildWidths(card, density, ctx, settings, widths)
            }
            card.addView(row)
        }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
