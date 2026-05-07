package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem
import kotlin.math.abs

/**
 * v16: cella folder che mostra fino a 4 icone in 2x2 dentro un quadrato arrotondato.
 * Tap = apre folder. Hold 600ms = drag. Hold 1200ms = (futuro: menu rinomina).
 */
class FolderCellView(context: Context) : LinearLayout(context) {

    private val previewView = FolderPreview(context)
    private val labelView = TextView(context)
    var folder: HomeItem? = null
        private set

    var onOpen: ((HomeItem) -> Unit)? = null
    var dragOriginId: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f; private var downY = 0f
    private var pressing = false; private var dragFired = false; private var moved = false

    private val DRAG_THRESHOLD = 600L

    private val dragRunnable = Runnable {
        if (pressing && !dragFired && dragOriginId.isNotEmpty()) {
            dragFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val f = folder ?: return@Runnable
            val data = ClipData.newPlainText("speedDrag", "$dragOriginId|${f.key}")
            startDragAndDrop(data, View.DragShadowBuilder(this), f.key, 0)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = (4 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        val previewSize = (48 * resources.displayMetrics.density).toInt()
        previewView.layoutParams = LayoutParams(previewSize, previewSize)
        addView(previewView)

        labelView.apply {
            textSize = 11.5f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setShadowLayer(2f, 0f, 1f, Color.argb(160, 0, 0, 0))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        addView(labelView)

        isClickable = true; isFocusable = true
    }

    fun bind(folder: HomeItem) {
        this.folder = folder
        labelView.text = if (folder.name.isNotEmpty()) folder.name else "Cartella"
        val apps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val byKey = apps.associateBy { it.key }
        val drawables = folder.folderApps.take(4).mapNotNull { byKey[it]?.icon }
        previewView.setIcons(drawables)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val f = folder ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                pressing = true; moved = false; dragFired = false
                handler.postDelayed(dragRunnable, DRAG_THRESHOLD)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX); val dy = abs(event.y - downY)
                if (!moved && (dx > moveSlop || dy > moveSlop)) {
                    moved = true
                    handler.removeCallbacks(dragRunnable)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressing = false
                handler.removeCallbacks(dragRunnable)
                if (moved || dragFired) return true
                onOpen?.invoke(f)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressing = false
                handler.removeCallbacks(dragRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Inner view: disegna fino a 4 icone in 2x2 dentro un quadrato arrotondato semi-trasparente.
     */
    private class FolderPreview(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FFFFFF")
        }
        private val rect = RectF()
        private var icons: List<Drawable> = emptyList()

        fun setIcons(list: List<Drawable>) {
            icons = list
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val r = w * 0.22f
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, r, r, bgPaint)

            if (icons.isEmpty()) return
            val pad = w * 0.12f
            val inner = w - 2 * pad
            val cellSize = (inner - pad * 0.4f) / 2f  // gap piccolo tra le icone
            val gap = pad * 0.4f
            val baseX = pad
            val baseY = pad
            for (i in 0 until icons.size.coerceAtMost(4)) {
                val col = i % 2
                val row = i / 2
                val x = baseX + col * (cellSize + gap)
                val y = baseY + row * (cellSize + gap)
                val d = icons[i]
                d.setBounds(x.toInt(), y.toInt(), (x + cellSize).toInt(), (y + cellSize).toInt())
                d.draw(canvas)
            }
        }
    }
}
