package org.cheipstudio.speedlauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v190: BottomSheet Pixel Material Expressive per ridimensionare/spostare il widget.
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

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(), (8 * density).toInt(),
                (16 * density).toInt(), (24 * density).toInt()
            )
        }
        
        // Drag handle
        root.addView(View(ctx).apply {
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        })

        // Title
        root.addView(TextView(ctx).apply {
            text = getString(R.string.widget_resize_title)
            textSize = 22f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.01f
            setPadding((8 * density).toInt(), 0, 0, (20 * density).toInt())
        })

        // Sezione Posizione
        addSection(root, getString(R.string.settings_widget_position), density)
        addCard(root, density,
            listOf(
                getString(R.string.widget_pos_top) to "top",
                getString(R.string.widget_pos_middle) to "middle",
                getString(R.string.widget_pos_bottom) to "bottom"
            )) { value ->
            settings.setWidgetPosition(value); onChanged?.invoke(); dismissAllowingStateLoss()
        }

        // Sezione Altezza
        addSection(root, getString(R.string.settings_widget_height), density)
        addCard(root, density,
            listOf(
                "Piccolo" to "100",
                "Medio" to "170",
                "Grande" to "240",
                "Extra" to "300"
            )) { value ->
            settings.setWidgetHeight(value.toInt()); onChanged?.invoke(); dismissAllowingStateLoss()
        }

        // Sezione Larghezza
        addSection(root, getString(R.string.settings_widget_width), density)
        addCard(root, density,
            listOf("50%" to "50", "75%" to "75", "100%" to "100")) { value ->
            settings.setWidgetWidthPercent(value.toInt()); onChanged?.invoke(); dismissAllowingStateLoss()
        }

        // Rimuovi (card distinta in rosso)
        addSection(root, "", density)
        val removeCard = MaterialCardView(ctx).apply {
            radius = 28f * density
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorErrorContainer))
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * density).toInt()
            layoutParams = lp
        }
        val removeText = TextView(ctx).apply {
            text = getString(R.string.widget_remove_action)
            textSize = 16f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnErrorContainer))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(
                (24 * density).toInt(), (18 * density).toInt(),
                (24 * density).toInt(), (18 * density).toInt()
            )
            val tvSel = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
            setBackgroundResource(tvSel.resourceId)
        }
        removeCard.addView(removeText)
        removeCard.setOnClickListener { onRemove?.invoke(); dismissAllowingStateLoss() }
        root.addView(removeCard)

        val scroll = androidx.core.widget.NestedScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(root)
        return scroll
    }

    private fun resolveColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun addSection(parent: LinearLayout, label: String, density: Float) {
        if (label.isNotBlank()) {
            parent.addView(TextView(parent.context).apply {
                text = label
                textSize = 13f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                isAllCaps = true
                letterSpacing = 0.08f
                setPadding(
                    (12 * density).toInt(), (16 * density).toInt(),
                    (12 * density).toInt(), (8 * density).toInt()
                )
            })
        } else {
            parent.addView(View(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (12 * density).toInt()
                )
            })
        }
    }

    private fun addCard(parent: LinearLayout, density: Float, items: List<Pair<String, String>>, onClick: (String) -> Unit) {
        val ctx = parent.context
        val card = MaterialCardView(ctx).apply {
            radius = 28f * density
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for ((label, value) in items) {
            val tv = TextView(ctx).apply {
                text = label
                textSize = 16f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                setPadding(
                    (24 * density).toInt(), (16 * density).toInt(),
                    (24 * density).toInt(), (16 * density).toInt()
                )
                val tvSel = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
                setBackgroundResource(tvSel.resourceId)
                isClickable = true
                setOnClickListener { onClick(value) }
            }
            ll.addView(tv)
        }
        card.addView(ll)
        parent.addView(card)
    }
}
