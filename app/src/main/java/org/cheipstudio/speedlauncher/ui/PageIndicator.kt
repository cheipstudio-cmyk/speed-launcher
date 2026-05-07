package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
    }
    private val dotRadius = 4 * resources.displayMetrics.density
    private val gap = 14 * resources.displayMetrics.density

    private var pageCount = 0
    private var activePage = 0

    fun setPages(count: Int, active: Int) {
        pageCount = count
        activePage = active
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (pageCount > 0) ((pageCount * dotRadius * 2) + ((pageCount - 1) * gap)).toInt() + paddingLeft + paddingRight else 0
        val height = (dotRadius * 2 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 1) return
        val cy = height / 2f
        var cx = paddingLeft + dotRadius
        for (i in 0 until pageCount) {
            val paint = if (i == activePage) activePaint else inactivePaint
            canvas.drawCircle(cx, cy, dotRadius, paint)
            cx += dotRadius * 2 + gap
        }
    }
}
