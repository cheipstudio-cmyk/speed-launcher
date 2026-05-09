package org.cheipstudio.speedlauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v181: BottomSheet per ridimensionare/spostare il widget. Long press su widget montato → questa.
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
                (16 * density).toInt(), (12 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
        }

        // Title
        root.addView(TextView(ctx).apply {
            text = getString(R.string.widget_resize_title)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        // SEZIONE: Posizione
        addSection(root, getString(R.string.settings_widget_position), density)
        addRow(root, getString(R.string.position_top), density) {
            settings.setWidgetPosition("top"); onChanged?.invoke(); dismissAllowingStateLoss()
        }
        addRow(root, getString(R.string.position_above_search), density) {
            settings.setWidgetPosition("above_search"); onChanged?.invoke(); dismissAllowingStateLoss()
        }
        addRow(root, getString(R.string.position_below_search), density) {
            settings.setWidgetPosition("below_search"); onChanged?.invoke(); dismissAllowingStateLoss()
        }

        // SEZIONE: Altezza
        addSection(root, getString(R.string.settings_widget_height), density)
        for ((label, value) in listOf("Piccolo (100dp)" to 100, "Medio (170dp)" to 170, "Grande (240dp)" to 240, "Extra (300dp)" to 300)) {
            addRow(root, label, density) {
                settings.setWidgetHeight(value); onChanged?.invoke(); dismissAllowingStateLoss()
            }
        }

        // SEZIONE: Larghezza
        addSection(root, getString(R.string.settings_widget_width), density)
        for ((label, value) in listOf("50%" to 50, "75%" to 75, "100%" to 100)) {
            addRow(root, label, density) {
                settings.setWidgetWidthPercent(value); onChanged?.invoke(); dismissAllowingStateLoss()
            }
        }

        // SEZIONE: Rimuovi
        addSection(root, "", density)
        addRow(root, getString(R.string.widget_remove_action), density, isDestructive = true) {
            onRemove?.invoke(); dismissAllowingStateLoss()
        }

        // Wrap in scroll
        val scroll = androidx.core.widget.NestedScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(root)
        return scroll
    }

    private fun addSection(parent: LinearLayout, label: String, density: Float) {
        if (label.isNotBlank()) {
            parent.addView(TextView(parent.context).apply {
                text = label
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#888888"))
                setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
                isAllCaps = true
            })
        } else {
            // Spacer
            parent.addView(View(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (8 * density).toInt()
                )
            })
        }
    }

    private fun addRow(parent: LinearLayout, label: String, density: Float, isDestructive: Boolean = false, onClick: () -> Unit) {
        val ctx = parent.context
        val tv = TextView(ctx).apply {
            text = label
            textSize = 16f
            setPadding(
                (8 * density).toInt(), (14 * density).toInt(),
                (8 * density).toInt(), (14 * density).toInt()
            )
            if (isDestructive) {
                setTextColor(android.graphics.Color.parseColor("#E53935"))
            }
            val tv2 = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv2, true)
            setBackgroundResource(tv2.resourceId)
            isClickable = true
            setOnClickListener { onClick() }
        }
        parent.addView(tv)
    }
}
