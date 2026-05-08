package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R

/**
 * v29: barra alfabetica laterale stile iOS Contacts.
 * Mostra le iniziali (A-Z + #) come piccole etichette su lato destro.
 * Trascinando il dito → callback con la lettera selezionata.
 * Mostra un overlay grande con la lettera corrente durante il drag.
 */
class AlphaScrollBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val density = resources.displayMetrics.density

    /**
     * Lista delle iniziali presenti nel dataset corrente, in ordine alfabetico.
     * Es: ["A", "B", "F", "M", "S", "Z"]
     */
    private var letters: List<String> = emptyList()

    /** Lettera attualmente selezionata (durante drag) */
    private var selectedLetter: String? = null

    /** Callback: chiamato quando l'utente seleziona una lettera */
    var onLetterSelected: ((String) -> Unit)? = null

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 11 * density
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selectedY: Float = 0f
    private var bubbleScale: Float = 1f
    private var bubbleAnimator: android.animation.ValueAnimator? = null
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    /** Resolve color attr al run-time */
    private fun colorAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun setLetters(list: List<String>) {
        letters = list
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // larghezza fissa 28dp, altezza match parent
        val w = (28 * density).toInt()
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        val totalH = height.toFloat()
        val itemH = totalH / letters.size

        // colore label normale (grigio) e selezionato (primary)
        val normalColor = colorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val selectedColor = colorAttr(com.google.android.material.R.attr.colorPrimary)

        for ((i, letter) in letters.withIndex()) {
            val cy = itemH * i + itemH / 2
            val isSelected = letter == selectedLetter
            labelPaint.color = if (isSelected) selectedColor else normalColor
            labelPaint.textSize = if (isSelected) 13 * density else 11 * density
            // bias verticale per posizionare il testo (baseline)
            val fm = labelPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2
            canvas.drawText(letter, width / 2f, textY, labelPaint)
        }

        // v51: card rialzata animata con la lettera selezionata
        val sel = selectedLetter
        if (sel != null) {
            // Posizione: alla altezza del dito (selectedY tracked da onTouch)
            val cardW = 84 * density
            val cardH = 84 * density
            val cx = -cardW - 12 * density  // a sinistra della scrollbar
            val cy = (selectedY).coerceIn(cardH / 2, totalH - cardH / 2)
            val left = cx
            val top = cy - cardH / 2
            val right = cx + cardW
            val bottom = cy + cardH / 2

            // Shadow per effetto "rialzata"
            shadowPaint.color = android.graphics.Color.argb(80, 0, 0, 0)
            shadowPaint.maskFilter = android.graphics.BlurMaskFilter(8 * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
            val shadowOffset = 4 * density
            canvas.drawRoundRect(
                RectF(left, top + shadowOffset, right, bottom + shadowOffset),
                24 * density, 24 * density, shadowPaint
            )

            // Card sfondo
            bubblePaint.color = colorAttr(com.google.android.material.R.attr.colorPrimaryContainer)
            val cardScale = bubbleScale  // animato
            val scaledW = cardW * cardScale
            val scaledH = cardH * cardScale
            canvas.drawRoundRect(
                RectF(cx + (cardW - scaledW) / 2, cy - scaledH / 2,
                      cx + (cardW + scaledW) / 2, cy + scaledH / 2),
                24 * density, 24 * density, bubblePaint
            )

            // Lettera grande
            bubbleTextPaint.color = colorAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
            bubbleTextPaint.textSize = 44 * density * cardScale
            bubbleTextPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            val fm = bubbleTextPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2
            canvas.drawText(sel, cx + cardW / 2, textY, bubbleTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val itemH = height.toFloat() / letters.size
                val idx = (event.y / itemH).toInt().coerceIn(0, letters.size - 1)
                val letter = letters[idx]
                selectedLetter = letter
                selectedY = event.y
                onLetterSelected?.invoke(letter)
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                // v51: animazione scale 0.6 → 1.0
                bubbleAnimator?.cancel()
                bubbleAnimator = android.animation.ValueAnimator.ofFloat(0.6f, 1f).apply {
                    duration = 180
                    interpolator = android.view.animation.OvershootInterpolator(1.6f)
                    addUpdateListener {
                        bubbleScale = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val itemH = height.toFloat() / letters.size
                val idx = (event.y / itemH).toInt().coerceIn(0, letters.size - 1)
                val letter = letters[idx]
                selectedY = event.y
                if (letter != selectedLetter) {
                    selectedLetter = letter
                    onLetterSelected?.invoke(letter)
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // v51: animazione scale out 1.0 → 0.4 + alpha → 0
                bubbleAnimator?.cancel()
                bubbleAnimator = android.animation.ValueAnimator.ofFloat(bubbleScale, 0f).apply {
                    duration = 150
                    interpolator = android.view.animation.AccelerateInterpolator()
                    addUpdateListener {
                        bubbleScale = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            selectedLetter = null
                            invalidate()
                        }
                    })
                    start()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
