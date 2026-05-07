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

        // overlay bubble grande con la lettera selezionata
        val sel = selectedLetter
        if (sel != null) {
            val bubbleSize = 56 * density
            val cx = -bubbleSize / 2 - 4 * density  // a sinistra della scrollbar
            val cy = totalH / 2
            // sfondo bolla
            bubblePaint.color = colorAttr(com.google.android.material.R.attr.colorPrimaryContainer)
            val rect = RectF(cx - bubbleSize / 2, cy - bubbleSize / 2,
                cx + bubbleSize / 2, cy + bubbleSize / 2)
            canvas.drawOval(rect, bubblePaint)
            // testo
            bubbleTextPaint.color = colorAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
            bubbleTextPaint.textSize = 28 * density
            val fm = bubbleTextPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2
            canvas.drawText(sel, cx, textY, bubbleTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val itemH = height.toFloat() / letters.size
                val idx = (event.y / itemH).toInt().coerceIn(0, letters.size - 1)
                val letter = letters[idx]
                if (letter != selectedLetter) {
                    selectedLetter = letter
                    onLetterSelected?.invoke(letter)
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedLetter = null
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
