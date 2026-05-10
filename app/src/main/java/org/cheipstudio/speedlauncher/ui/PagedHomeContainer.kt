package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.children
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v16: pager fluido con velocity tracking + snap.
 * - Snap automatico alla pagina più vicina al rilascio
 * - Fling >800px/s cambia pagina nella direzione
 * - Touch slop standard
 */
class PagedHomeContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    var currentPage: Int = 0
        private set
    val pageCount: Int get() = container.childCount
    var onPageChanged: ((Int) -> Unit)? = null
    
    // v250: leading page (RSS) prima della home page 0
    private var leadingView: android.view.View? = null
    val hasLeadingPage: Boolean get() = leadingView != null
    val leadingOffset: Int get() = if (hasLeadingPage) 1 else 0
    /** index logico (home page 0 = 0). Negativo se siamo sulla leading page (RSS). */
    val logicalPage: Int get() = currentPage - leadingOffset
    val homePageCount: Int get() = container.childCount - leadingOffset

    private val tracker = VelocityTracker.obtain()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(container, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun addPage(view: android.view.View) {
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
        // ogni pagina = 1 fattore di peso, ma con larghezza container = pageCount * width
        container.addView(view, lp)
        post { adjustChildWidths() }
    }
    
    /** v250: aggiunge una pagina speciale prima della pagina 0 (es. RSS). */
    fun addLeadingPage(view: android.view.View) {
        if (leadingView != null) removeLeadingPage()
        leadingView = view
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
        container.addView(view, 0, lp)
        post {
            adjustChildWidths()
            // dopo aggiunta scroll-to-skip leading page (rimaniamo su home 0)
            scrollTo(width, 0)
            currentPage = 1
        }
    }
    
    fun removeLeadingPage() {
        val v = leadingView ?: return
        leadingView = null
        container.removeView(v)
        post {
            adjustChildWidths()
            scrollTo(0, 0)
            currentPage = 0
        }
    }

    fun removePage(idx: Int) {
        if (idx in 0 until container.childCount) {
            container.removeViewAt(idx)
            post { adjustChildWidths() }
        }
    }

    private fun adjustChildWidths() {
        val w = width
        if (w == 0) return
        for (child in container.children) {
            child.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        container.requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        adjustChildWidths()
    }

    fun snapToPage(idx: Int, animate: Boolean = true) {
        val target = idx.coerceIn(0, max(0, container.childCount - 1))
        if (animate) smoothScrollTo(target * width, 0)
        else scrollTo(target * width, 0)
        if (target != currentPage) {
            currentPage = target
            onPageChanged?.invoke(target)
        }
    }
    
    /** v250: snap a una home page (ignora leading). idx=0 è la prima home reale. */
    fun snapToHomePage(idx: Int, animate: Boolean = true) {
        snapToPage(idx + leadingOffset, animate)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                dragging = false
                tracker.clear(); tracker.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!dragging && dx > touchSlop && dx > dy) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        tracker.addMovement(ev)
        when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker.computeCurrentVelocity(1000)
                val vx = tracker.xVelocity
                val pageW = width
                if (pageW > 0) {
                    val pageF = scrollX.toFloat() / pageW
                    var target = pageF.toInt()
                    val frac = pageF - target
                    target = when {
                        // fling rapido cambia pagina
                        vx < -800f -> target + 1
                        vx > 800f -> target
                        // altrimenti snap al più vicino
                        frac > 0.5f -> target + 1
                        else -> target
                    }
                    snapToPage(target.coerceIn(0, max(0, container.childCount - 1)))
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
