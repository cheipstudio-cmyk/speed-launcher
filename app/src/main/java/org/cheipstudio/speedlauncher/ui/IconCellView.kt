package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
    var onDragStart: ((AppInfo, View) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private var dragStarted = false
    private var menuOpened = false
    private var pressAnimated = false

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!dragStarted && !menuOpened) {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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

    fun bind(app: AppInfo) {
        this.app = app
        iconView.setImageDrawable(app.icon)
        labelView.text = app.label
        packageName = app.packageName
    }

    private fun cancelPressAnimation() {
        // Reset scale immediato
        iconView.animate().cancel()
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        pressAnimated = false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = app ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressFired = false
                dragStarted = false
                menuOpened = false
                pressAnimated = false
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressTimeout)
                // Press feedback ritardato — solo dopo touchSlop confermato
                postDelayed({
                    if (!dragStarted && !menuOpened) {
                        Anim.pressFeedback(iconView)
                        pressAnimated = true
                    }
                }, 80)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (longPressFired && !dragStarted && (dx > touchSlop || dy > touchSlop)) {
                    dragStarted = true
                    cancelPressAnimation()
                    onDragStart?.invoke(a, this)
                } else if (!longPressFired && (dx > touchSlop * 2 || dy > touchSlop * 2)) {
                    handler.removeCallbacks(longPressRunnable)
                    cancelPressAnimation()
                    longPressFired = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragStarted) {
                    // niente
                } else if (longPressFired && !menuOpened) {
                    menuOpened = true
                    onMenu?.invoke(a, this)
                } else if (!longPressFired) {
                    postDelayed({ onLaunch?.invoke(a, this) }, 50)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                // Importante: il parent ci ha cancellato il touch (es. swipe rilevato)
                handler.removeCallbacks(longPressRunnable)
                cancelPressAnimation()
                longPressFired = false
                dragStarted = false
                menuOpened = false
                return true
            }
        }
        return super.onTouchEvent(event)
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
