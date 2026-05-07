package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
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
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.SettingsRepository
import kotlin.math.abs

/**
 * v18:
 * - Long-press menu fix: 500ms drag, 1000ms menu (più tolleranti)
 * - Icon shape applicato via IconShaper
 * - Dot color custom da settings
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
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var pressing = false
    private var dragFired = false
    private var menuFired = false
    private var moved = false

    private val DRAG_THRESHOLD = 500L
    private val MENU_THRESHOLD = 1000L

    private val dragRunnable = Runnable {
        if (pressing && !dragFired && !menuFired && dragOriginId.isNotEmpty()) {
            dragFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val a = app ?: return@Runnable
            val data = ClipData.newPlainText("speedDrag", "$dragOriginId|${a.key}")
            startDragAndDrop(data, View.DragShadowBuilder(this), a.key, 0)
        }
    }
    private val menuRunnable = Runnable {
        if (pressing && !menuFired) {
            menuFired = true
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val a = app ?: return@Runnable
            onMenu?.invoke(a, this)
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
        val settings = SpeedApp.instance.settingsRepository
        dotPaint.color = settings.dotColor.value ?: SettingsRepository.DOT_DEFAULT
        isClickable = true
        isFocusable = true
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = app ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                pressing = true; moved = false
                dragFired = false; menuFired = false
                handler.postDelayed(dragRunnable, DRAG_THRESHOLD)
                handler.postDelayed(menuRunnable, MENU_THRESHOLD)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!moved && (dx > moveSlop || dy > moveSlop)) {
                    moved = true
                    handler.removeCallbacks(dragRunnable)
                    handler.removeCallbacks(menuRunnable)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressing = false
                handler.removeCallbacks(dragRunnable)
                handler.removeCallbacks(menuRunnable)
                if (moved || dragFired || menuFired) return true
                onLaunch?.invoke(a, this)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressing = false
                handler.removeCallbacks(dragRunnable)
                handler.removeCallbacks(menuRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun bind(app: AppInfo) {
        this.app = app
        val settings = SpeedApp.instance.settingsRepository
        val shape = settings.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL
        iconView.setImageDrawable(IconShaper.shape(app.icon, shape))
        labelView.text = app.label
        packageName = app.packageName
        dotPaint.color = settings.dotColor.value ?: SettingsRepository.DOT_DEFAULT
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
