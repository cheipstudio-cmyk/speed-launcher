package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import kotlin.math.abs

/**
 * v12 home icons: pattern Pixel-style.
 * - Tap (rilascio entro 250ms) → onLaunch IMMEDIATO
 * - Press tenuta 250-600ms → menu (onMenu)
 * - Press tenuta >600ms → drag (startDragAndDrop)
 *
 * Niente più doppio tap. Tutto basato sul tempo di pressione.
 */
class IconCellView(context: Context) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private val labelView = TextView(context)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastNotifCount: Int = 0
    var packageName: String = ""
        private set

    private var app: AppInfo? = null
    var onLaunch: ((AppInfo, View) -> Unit)? = null
    var onMenu: ((AppInfo, View) -> Unit)? = null

    var dragOriginId: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var pressing = false
    private var menuFired = false
    private var dragFired = false
    private var moved = false

    private val MENU_THRESHOLD = 250L
    private val DRAG_THRESHOLD = 600L

    private val menuRunnable = Runnable {
        if (pressing && !moved && !menuFired && !dragFired) {
            menuFired = true
            // Feedback aptico leggero
            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Animazione "selected" leggera
            iconView.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
        }
    }
    private val dragRunnable = Runnable {
        if (pressing && !moved && !dragFired && dragOriginId.isNotEmpty()) {
            dragFired = true
            menuFired = false  // se parte drag, niente menu
            iconView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val a = app ?: return@Runnable
            val data = ClipData.newPlainText("speedDrag", "$dragOriginId|${a.key}")
            startDragAndDrop(data, View.DragShadowBuilder(this), a.key, 0)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = (4 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        val iconSize = (48 * resources.displayMetrics.density).toInt()
        iconView.layoutParams = LayoutParams(iconSize, iconSize)
        addView(iconView)

        labelView.apply {
            textSize = 11.5f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setShadowLayer(2f, 0f, 1f, Color.argb(160, 0, 0, 0))
            val labelMargin = (4 * resources.displayMetrics.density).toInt()
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            params.topMargin = labelMargin
            layoutParams = params
        }
        addView(labelView)

        setWillNotDraw(false)
        dotPaint.color = ContextCompat.getColor(context, R.color.notification_dot)
        isClickable = true
        isFocusable = true
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = app ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressing = true
                moved = false
                menuFired = false
                dragFired = false
                handler.postDelayed(menuRunnable, MENU_THRESHOLD)
                handler.postDelayed(dragRunnable, DRAG_THRESHOLD)
                // Lascia che il parent scroll orizzontale possa intercettare
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!moved && (dx > touchSlop || dy > touchSlop)) {
                    moved = true
                    // Movimento → cancella tutto, lascia gestire al parent (paginazione orizzontale o swipe-up)
                    handler.removeCallbacks(menuRunnable)
                    handler.removeCallbacks(dragRunnable)
                    iconView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressing = false
                handler.removeCallbacks(menuRunnable)
                handler.removeCallbacks(dragRunnable)
                iconView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                if (moved) return true
                if (dragFired) return true  // drag già partito
                if (menuFired) {
                    // Menu mode: rilasciato dopo 250ms ma prima di 600ms → apri menu
                    if (dragOriginId.isEmpty()) {
                        // Drawer: il menu lo apre il long-click standard, non noi
                    }
                    onMenu?.invoke(a, this)
                } else {
                    // Tap rapido: lancia immediato
                    onLaunch?.invoke(a, this)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressing = false
                handler.removeCallbacks(menuRunnable)
                handler.removeCallbacks(dragRunnable)
                iconView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun bind(app: AppInfo) {
        this.app = app
        iconView.setImageDrawable(app.icon)
        labelView.text = app.label
        packageName = app.packageName
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = SpeedApp.instance.notificationCounter.countFor(packageName)
        if (count != lastNotifCount) {
            if (count > 0 && lastNotifCount == 0) Anim.bounceIn(iconView)
            lastNotifCount = count
        }
        if (count > 0 && iconView.width > 0) {
            val cx = iconView.right - (4 * resources.displayMetrics.density)
            val cy = iconView.top + (6 * resources.displayMetrics.density)
            val r = 5 * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, r, dotPaint)
        }
    }
}
