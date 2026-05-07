package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeLayoutStore
import org.cheipstudio.speedlauncher.databinding.ViewHomeBinding
import org.cheipstudio.speedlauncher.widgets.WidgetHostController
import kotlin.math.abs

/**
 * v4.2:
 * - Swipe-up funziona da qualsiasi punto della home (no zone)
 * - Long-press home a 800ms (più affidabile, meno falsi positivi)
 * - Soglia swipe più stringente per non confondersi con tap
 */
class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewHomeBinding =
        ViewHomeBinding.inflate(LayoutInflater.from(context), this)

    private val layoutStore = HomeLayoutStore(context)

    var onSwipeUp: (() -> Unit)? = null
    var onSearchTap: (() -> Unit)? = null
    var onHomeLongPress: (() -> Unit)? = null
    var onAppLongPressOnHome: ((AppInfo) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var swipeArmed = false
    private var swipeDetected = false
    private var longPressFired = false
    private var downOverChild = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // Long press a 800ms invece dei 500 di sistema
    private val longPressTimeout = 800L
    private val swipeUpThreshold = resources.displayMetrics.density * 70f

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!swipeDetected && !longPressFired && !downOverChild) {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onHomeLongPress?.invoke()
        }
    }

    init {
        binding.searchBar.setOnClickListener { onSearchTap?.invoke() }
        binding.iconGrid.onAppLaunch = { app, view ->
            SpeedApp.instance.appRepository.launch(app, view)
        }
        binding.iconGrid.onAppLongPress = { app, _ ->
            cancelHomeLongPress()
            onAppLongPressOnHome?.invoke(app)
        }
        binding.dock.onAppLaunch = { app, view ->
            SpeedApp.instance.appRepository.launch(app, view)
        }
        binding.dock.onAppLongPress = { app, _ ->
            cancelHomeLongPress()
            onAppLongPressOnHome?.invoke(app)
        }
        binding.iconGrid.setLayout(layoutStore.load())
        binding.dock.setLayout(layoutStore.loadDock())
    }

    fun cancelHomeLongPress() {
        handler.removeCallbacks(longPressRunnable)
        longPressFired = true
    }

    private fun isOverInteractiveChild(x: Float, y: Float): Boolean {
        return hitTest(binding.iconGrid, x, y) ||
                hitTest(binding.dock, x, y) ||
                hitTest(binding.widgetSlot, x, y) ||
                hitTest(binding.searchBar, x, y)
    }

    private fun hitTest(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val myLoc = IntArray(2)
        getLocationInWindow(myLoc)
        val left = loc[0] - myLoc[0]
        val top = loc[1] - myLoc[1]
        return x >= left && x <= left + view.width && y >= top && y <= top + view.height
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressFired = false
                swipeDetected = false
                swipeArmed = true
                downOverChild = isOverInteractiveChild(downX, downY)
                handler.removeCallbacks(longPressRunnable)
                if (!downOverChild) {
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeArmed || swipeDetected) return false
                val dx = abs(ev.x - downX)
                val dy = downY - ev.y
                if (dx > touchSlop || abs(dy) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
                // Swipe-up rilevato da QUALSIASI parte della home, basta movimento verticale forte
                if (dy > swipeUpThreshold && dy > dx * 1.3f) {
                    swipeDetected = true
                    longPressFired = true
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                swipeArmed = false
                return false
            }
        }
        return false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> return swipeDetected
            MotionEvent.ACTION_UP -> {
                if (swipeDetected) {
                    swipeDetected = false
                    swipeArmed = false
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSwipeUp?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeDetected = false
                swipeArmed = false
            }
        }
        return false
    }

    fun attachWidgetHost(host: WidgetHostController) {
        binding.widgetSlot.setHostController(host)
    }

    fun refreshApps(apps: List<AppInfo>) {
        binding.iconGrid.refresh(apps)
        binding.dock.refresh(apps)
    }

    fun refreshDots() {
        binding.iconGrid.invalidate()
        binding.dock.invalidate()
    }

    fun pinApp(app: AppInfo) = binding.iconGrid.pinApp(app)
    fun unpinApp(app: AppInfo) {
        binding.iconGrid.unpinApp(app)
        binding.dock.unpinApp(app)
    }
    fun isPinned(app: AppInfo) = binding.iconGrid.isPinned(app) || binding.dock.isPinned(app)
}
