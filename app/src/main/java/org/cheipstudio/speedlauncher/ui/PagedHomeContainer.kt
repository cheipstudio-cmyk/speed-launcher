package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs

class PagedHomeContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val pagesContainer: LinearLayout
    var currentPage: Int = 0
        private set

    var onPageChanged: ((Int) -> Unit)? = null

    private var startX = 0f
    private var startY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var horizontalDragLikely = false

    private val handler = Handler(Looper.getMainLooper())
    private var edgeScrollPending = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true

        pagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }
        addView(pagesContainer)

        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_LOCATION -> { handleDragEdgeScroll(event.x); true }
                DragEvent.ACTION_DRAG_EXITED -> { cancelEdgeScroll(); true }
                DragEvent.ACTION_DRAG_ENDED -> { cancelEdgeScroll(); true }
                else -> true
            }
        }
    }

    fun addPage(view: View) {
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
        view.layoutParams = lp
        pagesContainer.addView(view)
        post { layoutPages() }
    }

    fun pageAt(index: Int): View? =
        if (index in 0 until pagesContainer.childCount) pagesContainer.getChildAt(index) else null

    val pageCount: Int get() = pagesContainer.childCount

    private fun layoutPages() {
        val w = width
        if (w == 0) return
        for (i in 0 until pagesContainer.childCount) {
            val child = pagesContainer.getChildAt(i)
            val lp = child.layoutParams
            lp.width = w
            child.layoutParams = lp
        }
        pagesContainer.requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutPages()
    }

    fun snapToPage(page: Int, animate: Boolean = true) {
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val x = target * width
        if (animate) smoothScrollTo(x, 0) else scrollTo(x, 0)
        if (target != currentPage) {
            currentPage = target
            onPageChanged?.invoke(target)
        }
    }

    private fun handleDragEdgeScroll(x: Float) {
        val w = width.toFloat()
        if (w <= 0) return
        val edgeZone = w * 0.15f
        when {
            x < edgeZone && currentPage > 0 -> scheduleEdgeScroll(currentPage - 1)
            x > w - edgeZone && currentPage < pageCount - 1 -> scheduleEdgeScroll(currentPage + 1)
            else -> cancelEdgeScroll()
        }
    }

    private fun scheduleEdgeScroll(targetPage: Int) {
        if (edgeScrollPending) return
        edgeScrollPending = true
        handler.postDelayed({
            if (edgeScrollPending) snapToPage(targetPage, animate = true)
            edgeScrollPending = false
        }, 500L)
    }

    private fun cancelEdgeScroll() {
        edgeScrollPending = false
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Intercept più reattivo: se è chiaramente orizzontale (slop superato + dx > dy), prendi.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                horizontalDragLikely = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!horizontalDragLikely && dx > touchSlop && dx > dy * 1.3f) {
                    horizontalDragLikely = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val w = width
                if (w > 0) {
                    val nearest = ((scrollX + w / 2f) / w).toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    snapToPage(nearest, animate = true)
                }
            }
        }
        return handled
    }
}
