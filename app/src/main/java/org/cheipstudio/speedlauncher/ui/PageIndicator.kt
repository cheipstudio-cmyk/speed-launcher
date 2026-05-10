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
    var onLeadingTap: (() -> Unit)? = null  // v250: tap su dot speciale RSS
    private var hasLeading = false
    private var leadingActive = false  // se true, il dot RSS è quello pieno
    var onRssTap: (() -> Unit)? = null

    private var pageCount = 0
    private var currentIdx = 0
    private val density = resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setPages(count: Int, current: Int, hasLeading: Boolean = false, leadingActive: Boolean = false) {
        if (count == pageCount && current == currentIdx && 
            hasLeading == this.hasLeading && leadingActive == this.leadingActive) return
        pageCount = count
        currentIdx = current
        this.hasLeading = hasLeading
        this.leadingActive = leadingActive
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        val small = (8 * density).toInt()
        val active = (28 * density).toInt()
        val margin = (5 * density).toInt()
        
        // v254: dot leading per RSS - icona feed invece di cerchietto
        if (hasLeading) {
            val iconSize = (16 * density).toInt()
            val leadingDot = android.widget.ImageView(context).apply {
                setImageResource(org.cheipstudio.speedlauncher.R.drawable.ic_feed)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    if (leadingActive) Color.WHITE else Color.parseColor("#88FFFFFF")
                )
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                layoutParams = LayoutParams(iconSize, iconSize).apply {
                    leftMargin = margin
                    rightMargin = margin + (6 * density).toInt()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onLeadingTap?.invoke() }
            }
            addView(leadingDot)
        }
        
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * density
                    setColor(if (i == currentIdx && !leadingActive) Color.WHITE else Color.parseColor("#66FFFFFF"))
                }
                layoutParams = LayoutParams(if (i == currentIdx && !leadingActive) active else small, small).apply {
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
