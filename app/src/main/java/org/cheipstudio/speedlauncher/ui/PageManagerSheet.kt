package org.cheipstudio.speedlauncher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cheipstudio.speedlauncher.R

/**
 * v43: Sheet che mostra la lista pagine home, ognuna con un bottone rimuovi,
 * e in fondo un bottone "Aggiungi pagina".
 *
 * Usa BottomSheetDialog manuale (non Fragment) per semplicità.
 */
object PageManagerSheet {

    fun show(
        context: Context,
        getPageCount: () -> Int,
        getPageIconCount: (Int) -> Int,
        onAddPage: () -> Unit,
        onRemovePage: (Int) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(),
                       (20 * density).toInt(), (28 * density).toInt())
        }

        // Drag handle
        root.addView(View(context).apply {
            background = androidx.core.content.ContextCompat.getDrawable(
                context, R.drawable.bg_drag_handle
            )
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        })

        // Title
        root.addView(TextView(context).apply {
            text = context.getString(R.string.page_manager_title)
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.01f
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
        })

        // Subtitle
        root.addView(TextView(context).apply {
            text = context.getString(R.string.page_manager_sub)
            textSize = 13f
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (20 * density).toInt()
            layoutParams = lp
        })

        // Scrollable container per le pagine
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.weight = 0f
            layoutParams = lp
        }
        val pagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(pagesContainer)
        root.addView(scroll)

        // Funzione per rebuild della lista
        fun rebuildList() {
            pagesContainer.removeAllViews()
            val count = getPageCount()
            for (i in 0 until count) {
                pagesContainer.addView(buildPageRow(context, density, i, getPageIconCount(i), count > 1) {
                    // confirm before remove via dialog Material 3 themed
                    MaterialAlertDialogBuilder(
                        context,
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                    )
                        .setTitle(R.string.page_manager_confirm_remove_title)
                        .setMessage(context.getString(R.string.page_manager_confirm_remove_msg, i + 1))
                        .setPositiveButton(R.string.page_manager_remove) { _, _ ->
                            onRemovePage(i)
                            rebuildList()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                })
            }
        }
        rebuildList()

        // Bottone "Aggiungi pagina" Pixel-style filled tonal
        val addBtn = MaterialButton(context).apply {
            text = context.getString(R.string.page_manager_add)
            icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            cornerRadius = (32 * density).toInt()
            setBackgroundColor(resolveAttrColor(context, com.google.android.material.R.attr.colorSecondaryContainer))
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer))
            setIconTintResource(android.R.color.transparent)
            iconTint = android.content.res.ColorStateList.valueOf(
                resolveAttrColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            textSize = 15f
            val padV = (14 * density).toInt()
            setPadding(padding, padV, padding, padV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (16 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                onAddPage()
                rebuildList()
            }
        }
        root.addView(addBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun buildPageRow(
        context: Context,
        density: Float,
        index: Int,
        iconCount: Int,
        canRemove: Boolean,
        onRemove: () -> Unit
    ): View {
        val row = MaterialCardView(context).apply {
            radius = 28 * density
            cardElevation = 0f
            setCardBackgroundColor(resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(),
                       (8 * density).toInt(), (12 * density).toInt())
        }

        // Numero pagina come chip rotondo Material You
        val numberView = TextView(context).apply {
            text = (index + 1).toString()
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(resolveAttrColor(context, com.google.android.material.R.attr.colorPrimaryContainer))
            }
            val sz = (40 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                marginEnd = (16 * density).toInt()
            }
        }
        inner.addView(numberView)

        // Label e count icone
        val labelBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        labelBlock.addView(TextView(context).apply {
            text = context.getString(R.string.page_manager_page_label, index + 1)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.NORMAL)
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface))
        })
        labelBlock.addView(TextView(context).apply {
            text = context.resources.getQuantityString(
                R.plurals.page_manager_icons_count, iconCount, iconCount
            )
            textSize = 13f
            setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        inner.addView(labelBlock)

        // Bottone rimuovi
        val removeBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_delete_forever)
            setColorFilter(
                if (canRemove) resolveAttrColor(context, com.google.android.material.R.attr.colorError)
                else resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            alpha = if (canRemove) 1f else 0.4f
            isClickable = canRemove
            isFocusable = canRemove
            background = androidx.core.content.ContextCompat.getDrawable(
                context, android.R.color.transparent
            )
            val s = (44 * density).toInt()
            val lp = LinearLayout.LayoutParams(s, s)
            layoutParams = lp
            setPadding((10 * density).toInt(), (10 * density).toInt(),
                       (10 * density).toInt(), (10 * density).toInt())
            if (canRemove) {
                setOnClickListener { onRemove() }
            }
        }
        inner.addView(removeBtn)

        row.addView(inner)
        return row
    }

    private fun makeChipBg(context: Context, density: Float, color: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun resolveAttrColor(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
