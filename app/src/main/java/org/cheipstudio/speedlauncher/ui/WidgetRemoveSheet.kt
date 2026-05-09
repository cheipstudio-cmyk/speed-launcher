package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.cheipstudio.speedlauncher.R

/**
 * v144: BottomSheet per confermare rimozione widget — stesso stile di FolderSheet menu / AppActionsSheet
 */
object WidgetRemoveSheet {

    fun show(context: Context, onConfirmRemove: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val sheet = BottomSheetDialog(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (16 * density).toInt(),
                (24 * density).toInt(), (24 * density).toInt()
            )
        }

        // Drag handle
        val handle = View(context).apply {
            background = context.getDrawable(R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(handle)

        // Title
        val title = TextView(context).apply {
            text = context.getString(R.string.widget_remove_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
        }
        container.addView(title)

        // Subtitle
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.widget_remove_subtitle)
            textSize = 14f
            setTextColor(resolveAttrInt(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(subtitle)

        // Action: Rimuovi
        val removeRow = buildSheetRow(
            context,
            context.getString(R.string.widget_remove_action),
            R.drawable.ic_delete_forever,
            isDestructive = true
        ) {
            sheet.dismiss()
            onConfirmRemove()
        }
        container.addView(removeRow)

        // Action: Annulla
        val cancelRow = buildSheetRow(
            context,
            context.getString(android.R.string.cancel),
            R.drawable.ic_arrow_back,
            isDestructive = false
        ) {
            sheet.dismiss()
        }
        container.addView(cancelRow)

        sheet.setContentView(container)
        sheet.show()
    }

    private fun buildSheetRow(
        ctx: Context,
        label: String,
        iconRes: Int,
        isDestructive: Boolean,
        onClick: () -> Unit
    ): View {
        val density = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
        }
        val icon = ImageView(ctx).apply {
            setImageResource(iconRes)
            val s = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (16 * density).toInt()
            }
            if (isDestructive) {
                setColorFilter(android.graphics.Color.parseColor("#E53935"))
            } else {
                setColorFilter(resolveAttrInt(ctx, com.google.android.material.R.attr.colorOnSurface))
            }
        }
        row.addView(icon)
        val txt = TextView(ctx).apply {
            text = label
            textSize = 16f
            if (isDestructive) {
                setTextColor(android.graphics.Color.parseColor("#E53935"))
                setTypeface(typeface, Typeface.BOLD)
            } else {
                setTextColor(resolveAttrInt(ctx, com.google.android.material.R.attr.colorOnSurface))
            }
        }
        row.addView(txt)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun resolveAttrInt(ctx: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
