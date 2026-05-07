package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.DragEvent
import android.view.MotionEvent
import android.view.VelocityTracker
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
    private var velocityTracker: VelocityTracker? = null

    private val handler = Handler(Looper.getMainLooper())
    private var edgeScrollPending = false
    private var edgeScrollTarget = -1

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        pagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }
        addView(pagesContainer)
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

    override fun onDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_LOCATION -> { handleDragEdgeScroll(event.x); return true }
            DragEvent.ACTION_DRAG_EXITED -> { cancelEdgeScroll(); return true }
            DragEvent.ACTION_DRAG_ENDED -> { cancelEdgeScroll(); return true }
        }
        return super.onDragEvent(event)
    }

    private fun handleDragEdgeScroll(rawX: Float) {
        val w = width.toFloat()
        if (w <= 0) return
        val edgeZone = w * 0.18f
        val newTarget = when {
            rawX < edgeZone && currentPage > 0 -> currentPage - 1
            rawX > w - edgeZone && currentPage < pageCount - 1 -> currentPage + 1
            else -> -1
        }
        if (newTarget == -1) { cancelEdgeScroll(); return }
        if (edgeScrollPending && edgeScrollTarget == newTarget) return
        cancelEdgeScroll()
        edgeScrollPending = true
        edgeScrollTarget = newTarget
        handler.postDelayed({
            if (edgeScrollPending && edgeScrollTarget == newTarget) snapToPage(newTarget, animate = true)
            edgeScrollPending = false
            edgeScrollTarget = -1
        }, 400L)
    }

    private fun cancelEdgeScroll() {
        edgeScrollPending = false
        edgeScrollTarget = -1
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * v14: intercept più reattivo (slop standard, dx > dy soltanto).
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x; startY = ev.y
                horizontalDragLikely = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!horizontalDragLikely && dx > touchSlop && dx > dy) {
                    horizontalDragLikely = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        val handled = super.onTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val w = width
                if (w > 0) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f

                    val centerOffset = scrollX + w / 2f
                    val baseNearest = (centerOffset / w).toInt()

                    // Velocity-based: se swipi rapido, vai nella direzione del fling
                    val target = when {
                        vx < -800f -> (currentPage + 1).coerceAtMost(pageCount - 1)
                        vx > 800f -> (currentPage - 1).coerceAtLeast(0)
                        else -> baseNearest.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    }
                    snapToPage(target, animate = true)
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return handled
    }
}
