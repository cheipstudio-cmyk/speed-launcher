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
 * v19 LOGICA CORRETTA:
 * - 500ms premuto FERMO → entra in modalità "armed": haptic, l'icona può ora essere DRAG
 *   se ti muovi, oppure puoi continuare a tenere premuto fermo per 500ms ancora per il MENU.
 * - 1000ms premuto FERMO senza muoverti → MENU Info/Disinstalla
 * - Se in stato armed ti muovi → parte il drag (cancella il menu pending)
 *
 * Differenza fondamentale dalla v18: a 500ms NON facciamo partire il drag, lo "armiamo".
 * Questo permette al menu di scattare a 1000ms se l'utente non muove il dito.
 */
class IconCellView(context: Context) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private val labelView = TextView(context)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private var lastNotifCount: Int = 0
    var packageName: String = ""
        private set

    private var app: AppInfo? = null
    var onLaunch: ((AppInfo, View) -> Unit)? = null
    var onMenu: ((AppInfo, View) -> Unit)? = null
    var dragOriginId: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    /** v20: dopo armed serve un movimento più ampio per draggare, evita conflitto col menu */
    private val dragSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var pressing = false
    private var armed = false
    private var menuFired = false
    private var dragFired = false

    private val ARM_DELAY = 500L
    private val MENU_DELAY = 2000L

    private val armRunnable = Runnable {
        if (pressing && !menuFired && !dragFired) {
            armed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            scaleX = 0.92f; scaleY = 0.92f  // feedback visivo: armato
            animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }
    }

    private val menuRunnable = Runnable {
        if (pressing && armed && !menuFired && !dragFired) {
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
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        addView(labelView)

        setWillNotDraw(false)
        val s = SpeedApp.instance.settingsRepository
        dotPaint.color = s.dotColor.value ?: SettingsRepository.DOT_DEFAULT
        isClickable = true
        isFocusable = true
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = app ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                pressing = true; armed = false
                menuFired = false; dragFired = false
                handler.postDelayed(armRunnable, ARM_DELAY)
                handler.postDelayed(menuRunnable, MENU_DELAY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (armed && !dragFired && !menuFired) {
                    // dopo armed: serve movimento ampio (dragSlop) per evitare di rubare il menu
                    if ((dx > dragSlop || dy > dragSlop) && dragOriginId.isNotEmpty()) {
                        dragFired = true
                        handler.removeCallbacks(menuRunnable)
                        val data = ClipData.newPlainText("speedDrag", "$dragOriginId|${a.key}")
                        startDragAndDrop(data, View.DragShadowBuilder(this), a.key, 0)
                    }
                } else if (!armed && (dx > moveSlop || dy > moveSlop)) {
                    // movimento prima dell'arm = è uno scroll, cancella tutto
                    cancelAll()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasArmed = armed
                val wasMenu = menuFired
                val wasDrag = dragFired
                cancelAll()
                if (!wasArmed && !wasMenu && !wasDrag) {
                    // tap normale
                    onLaunch?.invoke(a, this)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelAll() {
        pressing = false; armed = false
        handler.removeCallbacks(armRunnable)
        handler.removeCallbacks(menuRunnable)
    }

    fun bind(app: AppInfo) {
        this.app = app
        val s = SpeedApp.instance.settingsRepository
        val shape = s.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL
        iconView.setImageDrawable(IconShaper.shape(app.icon, shape))
        labelView.text = app.label
        packageName = app.packageName
        dotPaint.color = s.dotColor.value ?: SettingsRepository.DOT_DEFAULT
    }

    /**
     * v33: badge disegnato come overlay drawable sull'iconView.
     * ViewOverlay è progettato per disegnare sopra il contenuto della view ed è la soluzione
     * "ufficiale" Android per badges. Affidabile dove dispatchDraw poteva fallire per
     * via di hardware layers / shadow layers / ottimizzazioni di rendering.
     */
    private val badgeDrawable = object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val count = SpeedApp.instance.notificationCounter.countFor(packageName)
            if (count <= 0) return
            val s = SpeedApp.instance.settingsRepository
            val mode = s.notificationBadgeMode.value ?: SettingsRepository.BADGE_DOT
            if (mode == SettingsRepository.BADGE_OFF) return
            val density = resources.displayMetrics.density
            val b = bounds
            // colore sempre aggiornato dalle settings
            dotPaint.color = s.dotColor.value ?: SettingsRepository.DOT_DEFAULT
            if (mode == SettingsRepository.BADGE_COUNT) {
                val displayCount = if (count > 99) "99+" else count.toString()
                val textSize = 10 * density
                dotTextPaint.textSize = textSize
                val textWidth = dotTextPaint.measureText(displayCount)
                val padH = 5 * density
                val padV = 2 * density
                val pillW = (textWidth + padH * 2).coerceAtLeast(16 * density)
                val pillH = textSize + padV * 2 + (2 * density)
                // posiziona angolo alto-destra dell'iconView
                val cx = b.right - pillW / 2 + (2 * density)
                val cy = b.top + pillH / 2 - (2 * density)
                val rect = android.graphics.RectF(
                    cx - pillW / 2, cy - pillH / 2,
                    cx + pillW / 2, cy + pillH / 2
                )
                canvas.drawRoundRect(rect, pillH / 2, pillH / 2, dotPaint)
                val fm = dotTextPaint.fontMetrics
                val textY = cy - (fm.ascent + fm.descent) / 2
                canvas.drawText(displayCount, cx, textY, dotTextPaint)
            } else {
                val cx = b.right - (4 * density)
                val cy = b.top + (6 * density)
                val r = 5 * density
                canvas.drawCircle(cx, cy, r, dotPaint)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
        @Deprecated("API")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    init {
        // v33: aggiungo il badge come overlay dell'iconView
        // ViewOverlay disegna il drawable SOPRA tutto il contenuto della view
        iconView.viewTreeObserver.addOnGlobalLayoutListener {
            if (iconView.width > 0 && iconView.height > 0) {
                badgeDrawable.setBounds(0, 0, iconView.width, iconView.height)
            }
        }
        iconView.overlay.add(badgeDrawable)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // v33: trigger refresh badge animation se count è cambiato
        val count = SpeedApp.instance.notificationCounter.countFor(packageName)
        if (count != lastNotifCount) {
            if (count > 0 && lastNotifCount == 0) Anim.bounceIn(iconView)
            lastNotifCount = count
            badgeDrawable.invalidateSelf()
        }
    }
}
