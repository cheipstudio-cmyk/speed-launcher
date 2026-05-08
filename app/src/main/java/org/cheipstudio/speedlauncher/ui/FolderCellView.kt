package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.SettingsRepository
import kotlin.math.abs

/**
 * v18: design moderno per le cartelle.
 * v95: aggiunto badge notifiche sulla preview (dot/count) se una qualsiasi
 *      delle app dentro la cartella ha notifiche attive.
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

    private val DRAG_THRESHOLD = 500L
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

    // v95: observer per badge notifiche, lifecycle-aware via attach/detach
    private val notifObserver = androidx.lifecycle.Observer<Map<String, Int>> {
        previewView.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            SpeedApp.instance.notificationCounter.counts.observeForever(notifObserver)
        } catch (_: Throwable) {}
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            SpeedApp.instance.notificationCounter.counts.removeObserver(notifObserver)
        } catch (_: Throwable) {}
    }

    fun bind(folder: HomeItem) {
        this.folder = folder
        labelView.text = if (folder.name.isNotEmpty()) folder.name else context.getString(
            org.cheipstudio.speedlauncher.R.string.folder_default_name
        )
        val apps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val byKey = apps.associateBy { it.key }
        val drawables = folder.folderApps.take(4).mapNotNull { byKey[it]?.icon }
        previewView.setIcons(drawables)
        // v95: passa anche i package name per il conteggio notifiche
        val pkgNames = folder.folderApps.mapNotNull { byKey[it]?.packageName }
        previewView.setFolderPackages(pkgNames)
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

    private class FolderPreview(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#33FFFFFF")
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private val badgeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5252")
            style = Paint.Style.FILL
        }
        private val badgePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5252")
            style = Paint.Style.FILL
        }
        private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private var icons: List<Drawable> = emptyList()
        private var folderPackages: List<String> = emptyList()
        private var maskPath: Path? = null
        private var size = 0

        fun setIcons(list: List<Drawable>) { icons = list; invalidate() }
        fun setFolderPackages(list: List<String>) { folderPackages = list; invalidate() }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            size = w
            maskPath = buildSquirclePath(w.toFloat())
            // gradiente verticale
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF")),
                null, Shader.TileMode.CLAMP
            )
            badgeTextPaint.textSize = w * 0.22f
        }

        override fun onDraw(canvas: Canvas) {
            val s = size.toFloat()
            if (s == 0f) return
            val path = maskPath ?: return

            // ombra leggera sotto
            canvas.save()
            canvas.translate(0f, 2f)
            canvas.drawPath(path, shadowPaint)
            canvas.restore()

            // bg gradient
            canvas.drawPath(path, bgPaint)
            // bordo
            canvas.drawPath(path, borderPaint)

            // icone in 2x2
            if (icons.isNotEmpty()) {
                canvas.save()
                canvas.clipPath(path)
                val pad = s * 0.14f
                val gap = s * 0.06f
                val cellSize = (s - 2 * pad - gap) / 2f
                for (i in 0 until icons.size.coerceAtMost(4)) {
                    val col = i % 2
                    val row = i / 2
                    val x = pad + col * (cellSize + gap)
                    val y = pad + row * (cellSize + gap)
                    val d = icons[i]
                    d.setBounds(x.toInt(), y.toInt(), (x + cellSize).toInt(), (y + cellSize).toInt())
                    d.draw(canvas)
                }
                canvas.restore()
            }

            // v95: badge notifiche se almeno un'app dentro la cartella ha notifiche
            drawNotificationBadge(canvas, s)
        }

        private fun drawNotificationBadge(canvas: Canvas, s: Float) {
            val counter = try { SpeedApp.instance.notificationCounter } catch (_: Throwable) { return }
            val settings = try { SpeedApp.instance.settingsRepository } catch (_: Throwable) { return }
            val mode = settings.notificationBadgeMode.value ?: SettingsRepository.BADGE_DOT
            if (mode == SettingsRepository.BADGE_OFF) return

            // Somma notifiche di tutte le app dentro la cartella
            var totalCount = 0
            for (pkg in folderPackages) {
                totalCount += counter.countFor(pkg)
            }
            if (totalCount <= 0) return

            // Colore custom da settings (dotColor è un Int)
            try {
                val col = settings.dotColor.value ?: SettingsRepository.DOT_DEFAULT
                badgeDotPaint.color = col
                badgePillPaint.color = col
            } catch (_: Throwable) {}

            if (mode == SettingsRepository.BADGE_COUNT) {
                // pill in alto a destra
                val text = if (totalCount > 99) "99+" else totalCount.toString()
                val tw = badgeTextPaint.measureText(text)
                val ph = s * 0.26f
                val pw = (tw + s * 0.18f).coerceAtLeast(ph)
                val rect = RectF(s - pw - 2f, 2f, s - 2f, ph + 2f)
                canvas.drawRoundRect(rect, ph / 2, ph / 2, badgePillPaint)
                val baseline = rect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
                canvas.drawText(text, rect.centerX(), baseline, badgeTextPaint)
            } else {
                // dot in alto a destra
                val r = s * 0.10f
                canvas.drawCircle(s - r - 2f, r + 2f, r, badgeDotPaint)
            }
        }

        private fun buildSquirclePath(s: Float): Path {
            val path = Path()
            val n = 4f
            val r = s / 2
            val cx = r; val cy = r
            val steps = 64
            var first = true
            for (i in 0..steps) {
                val t = i.toFloat() / steps * (Math.PI * 2)
                val cosT = Math.cos(t); val sinT = Math.sin(t)
                val x = cx + Math.signum(cosT) * Math.pow(Math.abs(cosT), 2.0 / n) * r
                val y = cy + Math.signum(sinT) * Math.pow(Math.abs(sinT), 2.0 / n) * r
                if (first) { path.moveTo(x.toFloat(), y.toFloat()); first = false }
                else path.lineTo(x.toFloat(), y.toFloat())
            }
            path.close()
            return path
        }
    }
}
