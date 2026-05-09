package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * v16: PageIndicator tappabile. Ogni dot è un FrameLayout cliccabile.
 */
class PageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onPageTap: ((Int) -> Unit)? = null
    var onRssTap: (() -> Unit)? = null
    private var showRssIndicator = false

    private var pageCount = 0
    private var currentIdx = 0
    private val density = resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setPages(count: Int, current: Int) {
        if (count == pageCount && current == currentIdx) return
        pageCount = count
        currentIdx = current
        rebuild()
    }
    
    fun setShowRssIndicator(show: Boolean) {
        if (show == showRssIndicator) return
        showRssIndicator = show
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        val small = (8 * density).toInt()
        val active = (24 * density).toInt()
        val margin = (4 * density).toInt()
        
        // v172: indicatore RSS a sinistra (icona feed se attivato)
        if (showRssIndicator) {
            val rssDot = View(context).apply {
                val sz = small
                layoutParams = LayoutParams(sz, sz).apply {
                    rightMargin = (12 * density).toInt()
                    leftMargin = margin
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.WHITE)
                    alpha = 140
                    setStroke((1 * density).toInt(), android.graphics.Color.WHITE)
                }
                setOnClickListener { onRssTap?.invoke() }
            }
            addView(rssDot)
        }
        
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * density
                    setColor(if (i == currentIdx) Color.WHITE else Color.parseColor("#66FFFFFF"))
                }
                layoutParams = LayoutParams(if (i == currentIdx) active else small, small).apply {
                    leftMargin = margin; rightMargin = margin
                }
                isClickable = true
                isFocusable = true
                val tappable = (24 * density).toInt()
                setPadding(tappable / 4, tappable / 4, tappable / 4, tappable / 4)
                setOnClickListener { onPageTap?.invoke(i) }
            }
            addView(dot)
        }
    }
}
