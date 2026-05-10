package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v231: AppWidgetResizeFrame ispirato a Launcher3.
 * Overlay trasparente sopra il widget durante "edit mode":
 * - 4 maniglie (top, bottom, left, right) draggabili per ridimensionare
 * - Cornice tratteggiata semi-trasparente
 * - Tap fuori → esce edit mode
 *
 * Comunica con WidgetSlotView tramite callbacks.
 */
class WidgetEditOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onHeightChange: ((Int) -> Unit)? = null   // newHeightDp
    var onWidthChange: ((Int) -> Unit)? = null    // newPercent (50/75/100)
    var onDismiss: (() -> Unit)? = null
    var onMoveToPage: ((Int) -> Unit)? = null     // pageIndex relativo (-1 / +1)
    var onOpenPersonalize: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val handleSize = (28 * density).toInt()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = resolveColor(com.google.android.material.R.attr.colorPrimary)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f * density, 6f * density), 0f)
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = resolveColor(com.google.android.material.R.attr.colorPrimary)
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }
    private val borderRect = RectF()

    private var heightDp: Int = 160
    private var widthPercent: Int = 100
    private var minHeightDp = 80
    private var maxHeightDp = 360
    private var initialFingerY = 0f
    private var initialHeightDp = heightDp
    private var initialFingerX = 0f
    private var dragMode: DragMode = DragMode.NONE

    private enum class DragMode { NONE, RESIZE_TOP, RESIZE_BOTTOM, RESIZE_LEFT, RESIZE_RIGHT }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        // Bottom bar con Personalizza / Rimuovi
        post { addBottomBar() }
    }
    
    private fun addBottomBar() {
        val ctx = context
        val d = density
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (-44 * d).toInt()  // sotto il widget
            layoutParams = lp
        }
        val btnEdit = makeButton(ctx, ctx.getString(org.cheipstudio.speedlauncher.R.string.widget_personalize)) {
            onOpenPersonalize?.invoke()
        }
        val btnRemove = makeButton(ctx, ctx.getString(org.cheipstudio.speedlauncher.R.string.widget_remove_action)) {
            onRemove?.invoke()
        }
        bar.addView(btnEdit)
        val sep = View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams((8 * d).toInt(), 1)
        }
        bar.addView(sep)
        bar.addView(btnRemove)
        addView(bar)
    }
    
    private fun makeButton(ctx: Context, label: String, action: () -> Unit): View {
        val d = density
        return com.google.android.material.button.MaterialButton(ctx).apply {
            text = label
            cornerRadius = (24 * d).toInt()
            isAllCaps = false
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            insetTop = 0; insetBottom = 0
            val padH = (16 * d).toInt()
            val padV = (8 * d).toInt()
            setPadding(padH, padV, padH, padV)
            setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setOnClickListener { action() }
        }
    }

    fun configure(heightDp: Int, widthPercent: Int) {
        this.heightDp = heightDp
        this.widthPercent = widthPercent
        invalidate()
    }

    private fun resolveColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 4f * density
        borderRect.set(pad, pad, width - pad, height - pad)
        // bordo tratteggiato attorno al widget
        canvas.drawRoundRect(borderRect, 24f * density, 24f * density, borderPaint)
        // maniglie su 4 lati
        drawHandle(canvas, width / 2f, pad)                    // top
        drawHandle(canvas, width / 2f, height - pad)            // bottom
        drawHandle(canvas, pad, height / 2f)                   // left
        drawHandle(canvas, width - pad, height / 2f)            // right
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        val r = (handleSize / 2f) * 0.7f
        canvas.drawCircle(cx, cy, r, handleFillPaint)
        canvas.drawCircle(cx, cy, r, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = whichHandle(event.x, event.y)
                if (dragMode == DragMode.NONE) {
                    // tap fuori
                    return true
                }
                initialFingerX = event.rawX
                initialFingerY = event.rawY
                initialHeightDp = heightDp
                HapticHelper.tick(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode == DragMode.NONE) return true
                handleResizeDrag(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode == DragMode.NONE) {
                    // tap fuori → dismiss
                    onDismiss?.invoke()
                }
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun whichHandle(x: Float, y: Float): DragMode {
        val tol = handleSize.toFloat()
        return when {
            x in (width / 2f - tol)..(width / 2f + tol) && y < tol * 1.5 -> DragMode.RESIZE_TOP
            x in (width / 2f - tol)..(width / 2f + tol) && y > height - tol * 1.5 -> DragMode.RESIZE_BOTTOM
            y in (height / 2f - tol)..(height / 2f + tol) && x < tol * 1.5 -> DragMode.RESIZE_LEFT
            y in (height / 2f - tol)..(height / 2f + tol) && x > width - tol * 1.5 -> DragMode.RESIZE_RIGHT
            else -> DragMode.NONE
        }
    }

    private fun handleResizeDrag(rawX: Float, rawY: Float) {
        when (dragMode) {
            DragMode.RESIZE_BOTTOM -> {
                val deltaPx = rawY - initialFingerY
                val deltaDp = (deltaPx / density).toInt()
                val newH = (initialHeightDp + deltaDp).coerceIn(minHeightDp, maxHeightDp)
                if (newH != heightDp) {
                    heightDp = newH
                    onHeightChange?.invoke(newH)
                    HapticHelper.tick(this)
                }
            }
            DragMode.RESIZE_TOP -> {
                val deltaPx = initialFingerY - rawY
                val deltaDp = (deltaPx / density).toInt()
                val newH = (initialHeightDp + deltaDp).coerceIn(minHeightDp, maxHeightDp)
                if (newH != heightDp) {
                    heightDp = newH
                    onHeightChange?.invoke(newH)
                    HapticHelper.tick(this)
                }
            }
            DragMode.RESIZE_LEFT, DragMode.RESIZE_RIGHT -> {
                val deltaPx = if (dragMode == DragMode.RESIZE_RIGHT) rawX - initialFingerX else initialFingerX - rawX
                // mappa delta a percentuali: 50/75/100
                val newPct = when {
                    deltaPx > 80 * density -> 100
                    deltaPx > 0 -> 75
                    deltaPx < -80 * density -> 50
                    else -> widthPercent
                }
                if (newPct != widthPercent) {
                    widthPercent = newPct
                    onWidthChange?.invoke(newPct)
                    HapticHelper.tick(this)
                }
            }
            DragMode.NONE -> {}
        }
    }
}
