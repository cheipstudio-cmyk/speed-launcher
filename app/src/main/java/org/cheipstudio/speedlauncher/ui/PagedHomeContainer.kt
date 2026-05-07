package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Container orizzontale che ospita N pagine (le IconGridView) con snap manuale.
 * Ogni figlio occupa l'intera larghezza del viewport.
 *
 * Espone:
 * - addPage(view): aggiunge una pagina (chiamare prima di onMeasure se possibile)
 * - currentPage: pagina attualmente visualizzata
 * - onPageChanged: callback quando la pagina cambia
 */
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
    private var horizontalDragLikely = false

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

    fun addPage(view: android.view.View) {
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
        // Usiamo width = match parent del PagedHomeContainer dopo il measure
        view.layoutParams = lp
        pagesContainer.addView(view)
        post { layoutPages() }
    }

    fun pageAt(index: Int): android.view.View? {
        return if (index in 0 until pagesContainer.childCount) pagesContainer.getChildAt(index) else null
    }

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

    /**
     * Intercettiamo gli eventi: se è uno scroll orizzontale forte lo gestiamo,
     * altrimenti lo lasciamo passare ai figli (per il drag delle icone).
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
                // Solo se è chiaramente orizzontale prendiamo l'evento
                if (!horizontalDragLikely && dx > dy * 1.5f && dx > 24) {
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
                // Snap alla pagina più vicina
                val w = width
                if (w > 0) {
                    val nearest = ((scrollX + w / 2f) / w).toInt().coerceIn(0, pageCount - 1)
                    snapToPage(nearest, animate = true)
                }
            }
        }
        return handled
    }
}
