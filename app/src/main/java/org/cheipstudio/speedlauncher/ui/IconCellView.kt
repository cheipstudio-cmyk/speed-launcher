package org.cheipstudio.speedlauncher.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo

/**
 * v9:
 * - Sulla home (dragOriginId valorizzato):
 *   - Tap singolo → onLaunch
 *   - Doppio tap → onMenu (menu Info / Disinstalla)
 *   - Long-press → drag immediato
 * - Nel drawer (dragOriginId vuoto):
 *   - Tap → onLaunch
 *   - Long-press → menu (Info / Disinstalla)
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
        set(value) {
            field = value
            updateLongPressBehavior()
        }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTapTime: Long = 0L
    private val doubleTapTimeout = 300L
    private var pendingTapRunnable: Runnable? = null

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
        isLongClickable = true

        updateClickBehavior()
        updateLongPressBehavior()
    }

    private fun updateClickBehavior() {
        if (dragOriginId.isNotEmpty()) {
            // Sulla home: gestione doppio tap
            setOnClickListener {
                val a = app ?: return@setOnClickListener
                val now = System.currentTimeMillis()
                if (now - lastTapTime < doubleTapTimeout) {
                    // Doppio tap → menu
                    pendingTapRunnable?.let { handler.removeCallbacks(it) }
                    pendingTapRunnable = null
                    lastTapTime = 0L
                    onMenu?.invoke(a, this)
                } else {
                    // Primo tap: aspetta per vedere se arriva il secondo
                    lastTapTime = now
                    pendingTapRunnable = Runnable {
                        Anim.pressFeedback(iconView)
                        postDelayed({ onLaunch?.invoke(a, this) }, 50)
                        pendingTapRunnable = null
                    }
                    handler.postDelayed(pendingTapRunnable!!, doubleTapTimeout)
                }
            }
        } else {
            // Nel drawer: tap singolo lancia
            setOnClickListener {
                val a = app ?: return@setOnClickListener
                Anim.pressFeedback(iconView)
                postDelayed({ onLaunch?.invoke(a, this) }, 50)
            }
        }
    }

    private fun updateLongPressBehavior() {
        if (dragOriginId.isNotEmpty()) {
            // Sulla home: long-press = drag immediato
            setOnLongClickListener {
                val a = app ?: return@setOnLongClickListener false
                // Cancella eventuale tap pendente per evitare lancio in concomitanza
                pendingTapRunnable?.let { handler.removeCallbacks(it) }
                pendingTapRunnable = null
                lastTapTime = 0L
                val data = ClipData.newPlainText("speedDrag", "$dragOriginId|${a.key}")
                startDragAndDrop(data, View.DragShadowBuilder(this), a.key, 0)
                true
            }
        } else {
            // Nel drawer: long-press = menu
            setOnLongClickListener {
                val a = app ?: return@setOnLongClickListener false
                onMenu?.invoke(a, this)
                true
            }
        }
        updateClickBehavior()
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
