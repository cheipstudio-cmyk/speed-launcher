package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.widgets.WidgetHostController
import kotlin.math.abs
import kotlin.math.max

/**
 * v18: slot widget adaptive.
 * Default altezza 170dp ma se il widget richiede più, lo slot si espande fino a 280dp.
 * Sotto 170dp di richiesta lo slot resta a 170dp (centrato).
 *
 * Il widget viene istruito con updateAppWidgetSize per accomodarsi alla nostra dimensione.
 */
class WidgetSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var hostController: WidgetHostController? = null
    private var currentWidgetView: View? = null
    private var currentWidgetId: Int = -1
    private val placeholder: LinearLayout
    private val density = resources.displayMetrics.density

    private var pressX = 0f
    private var pressY = 0f
    private val holdHandler = Handler(Looper.getMainLooper())
    private val holdRunnable = Runnable {
        if (currentWidgetView != null) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showResizeSheet()
        }
    }

    init {
        placeholder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_widget_placeholder)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_widgets)
            setColorFilter(Color.parseColor("#88FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                (28 * density).toInt(), (28 * density).toInt()
            )
        }
        placeholder.addView(icon)
        val text = TextView(context).apply {
            setText(R.string.widget_placeholder_hint)
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (6 * density).toInt()
            layoutParams = lp
        }
        placeholder.addView(text)
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // v182: long-press su slot vuoto = picker; su widget montato = holdRunnable in dispatchTouchEvent
        setOnLongClickListener {
            if (currentWidgetView == null) {
                showCustomPicker()
                true
            } else false
        }
        isLongClickable = true
    }

    fun setHostController(controller: WidgetHostController) {
        hostController = controller
        // v74: prova a restorare il widget esistente se l\'app è stata aggiornata
        try {
            val view = controller.restoreWidget()
            if (view != null) {
                placeholder.visibility = View.GONE
                removeView(currentWidgetView)
                currentWidgetView = view
                addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (currentWidgetView != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressX = ev.x; pressY = ev.y
                    holdHandler.postDelayed(holdRunnable, 700L)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - pressX); val dy = abs(ev.y - pressY)
                    val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
                    if (dx > slop || dy > slop) holdHandler.removeCallbacks(holdRunnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdHandler.removeCallbacks(holdRunnable)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showCustomPicker() {
        val activity = context as? FragmentActivity ?: return
        // v20: passiamo larghezza E altezza dello slot per filtrare correttamente
        val sheet = WidgetPickerSheet.newInstance(width, height)
        sheet.onWidgetSelected = { info -> bindAndAdd(info) }
        sheet.show(activity.supportFragmentManager, "widget_picker")
    }

    private fun bindAndAdd(info: AppWidgetProviderInfo) {
        val controller = hostController ?: return
        val activity = context as? Activity ?: return
        val appWidgetId = controller.host.allocateAppWidgetId()
        val canBind = controller.appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (!canBind) {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            controller.pendingBindWidget = info
            controller.pendingBindAppWidgetId = appWidgetId
            controller.pendingPlaceCallback = { v -> placeWidgetView(v, info) }
            activity.startActivityForResult(bindIntent, WidgetHostController.REQ_BIND)
            return
        }
        if (info.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            // v210: salvo pendingId per recuperare in REQ_CONFIGURE quando data è null
            controller.pendingBindWidget = info
            controller.pendingBindAppWidgetId = appWidgetId
            controller.pendingPlaceCallback = { v -> placeWidgetView(v, info) }
            activity.startActivityForResult(configIntent, WidgetHostController.REQ_CONFIGURE)
        } else {
            val view = controller.createView(appWidgetId, info)
            view.setAppWidget(appWidgetId, info)
            controller.markLastWidget(appWidgetId)
            placeWidgetView(view, info)
        }
    }

    /**
     * v18: imposta dimensioni dello slot in base al widget e notifica il widget.
     */
    private fun placeWidgetView(view: View?, info: AppWidgetProviderInfo? = null) {
        view ?: return
        currentWidgetView = view
        currentWidgetId = hostController?.lastWidgetId ?: -1

        // calcola altezza ottimale
        val targetH = calcOptimalHeight(info)
        val lp = layoutParams
        if (lp != null && lp.height != targetH) {
            lp.height = targetH
            layoutParams = lp
        }

        removeAllViews()
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // v212: notifica widget di adattarsi (post per garantire width != 0)
        if (info != null && currentWidgetId != -1) {
            post {
                try {
                    val mgr = AppWidgetManager.getInstance(context)
                    // Width fallback: se width=0, usa screen width
                    val effectiveWidth = if (width > 0) width else context.resources.displayMetrics.widthPixels
                    val widthDp = (effectiveWidth / density).toInt().coerceAtLeast(40)
                    val heightDp = (targetH / density).toInt().coerceAtLeast(40)
                    val opts = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    }
                    mgr.updateAppWidgetOptions(currentWidgetId, opts)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun calcOptimalHeight(info: AppWidgetProviderInfo?): Int {
        val defaultH = (170 * density).toInt()
        val maxH = (280 * density).toInt()
        if (info == null) return defaultH
        // info.minHeight è in pixel, ma rappresenta dp. Su API moderni convert.
        val requestedPx = info.minHeight
        return when {
            requestedPx <= 0 -> defaultH
            requestedPx <= defaultH -> defaultH
            requestedPx >= maxH -> maxH
            else -> requestedPx
        }
    }

    private fun removeWidget() {
        val controller = hostController ?: return
        if (currentWidgetId != -1) {
            controller.deleteWidget(currentWidgetId)
            currentWidgetId = -1
        }
        currentWidgetView = null
        // ripristina dimensione default
        val lp = layoutParams
        if (lp != null) {
            lp.height = (170 * density).toInt()
            layoutParams = lp
        }
        removeAllViews()
        // v157: forza placeholder visibile dopo rimozione (era invisible nel parent)
        placeholder.visibility = View.VISIBLE
        // Stacca dal parent se per caso era ancora attached
        (placeholder.parent as? android.view.ViewGroup)?.removeView(placeholder)
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        requestLayout()
        invalidate()
    }

    private fun showResizeSheet() {
        try {
            val activity = (context as? androidx.fragment.app.FragmentActivity) ?: return
            val sheet = org.cheipstudio.speedlauncher.ui.WidgetResizeSheet()
            sheet.onChanged = {
                // re-applico config widget al cambio  
                try {
                    org.cheipstudio.speedlauncher.SpeedApp.instance
                    (parent as? android.view.View)?.requestLayout()
                } catch (_: Throwable) {}
            }
            sheet.onRemove = {
                org.cheipstudio.speedlauncher.ui.WidgetRemoveSheet.show(context) {
                    removeWidget()
                }
            }
            sheet.show(activity.supportFragmentManager, "widget_resize")
        } catch (_: Throwable) {}
    }
}
