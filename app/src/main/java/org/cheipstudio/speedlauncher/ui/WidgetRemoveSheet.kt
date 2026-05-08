package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.cheipstudio.speedlauncher.R

/**
 * v19: design moderno coerente, niente più rosa.
 * Card scura con icona warning, titolo+desc, due bottoni (annulla outline / rimuovi solid grigio).
 */
object WidgetRemoveSheet {

    fun show(context: Context, onConfirm: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_modal_sheet)
            setPadding(
                (28 * density).toInt(), (12 * density).toInt(),
                (28 * density).toInt(), (28 * density).toInt()
            )
        }

        // handle
        val handle = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (24 * density).toInt()
            layoutParams = lp
        }
        container.addView(handle)

        // icona
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_widgets)
            setColorFilter(resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
        }
        container.addView(icon)

        val title = TextView(context).apply {
            text = context.getString(R.string.widget_remove_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
        }
        container.addView(title)

        val msg = TextView(context).apply {
            text = context.getString(R.string.widget_remove_message)
            textSize = 14f
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (28 * density).toInt()
            val hpad = (8 * density).toInt()
            setPadding(hpad, 0, hpad, 0)
            layoutParams = lp
        }
        container.addView(msg)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(android.R.string.cancel)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_modal_btn_outline)
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = (8 * density).toInt()
            layoutParams = lp
        }

        val removeBtn = TextView(context).apply {
            text = context.getString(R.string.widget_remove_confirm)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnPrimary))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_modal_btn_solid)
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = (8 * density).toInt()
            layoutParams = lp
        }

        btnRow.addView(cancelBtn); btnRow.addView(removeBtn)
        container.addView(btnRow)

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(container)
        cancelBtn.setOnClickListener { dialog.dismiss() }
        removeBtn.setOnClickListener { onConfirm(); dialog.dismiss() }
        dialog.show()
    }

    private fun resolveAttr(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
