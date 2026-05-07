package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeLayoutStore
import org.cheipstudio.speedlauncher.databinding.ViewHomeBinding
import org.cheipstudio.speedlauncher.widgets.WidgetHostController
import kotlin.math.abs

/**
 * v5: gesture handling tramite GestureDetector standard.
 * - onFling con velocityY < -1500 → swipe-up (apri drawer)
 * - onLongPress su area vuota → menu home
 * - i figli (icone, dock, widget, search) gestiscono i loro tap normalmente
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

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            // Swipe verso l'alto: velocity Y negativa, e più verticale che orizzontale
            if (velocityY < -1500f && abs(velocityY) > abs(velocityX) * 1.3f) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onSwipeUp?.invoke()
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            // Long press solo se non sta sopra a un figlio interattivo
            if (!isOverInteractiveChild(e.x, e.y)) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onHomeLongPress?.invoke()
            }
        }
    })

    init {
        binding.searchBar.setOnClickListener { onSearchTap?.invoke() }
        binding.iconGrid.onAppLaunch = { app, view ->
            SpeedApp.instance.appRepository.launch(app, view)
        }
        binding.iconGrid.onAppLongPress = { app, _ ->
            onAppLongPressOnHome?.invoke(app)
        }
        binding.dock.onAppLaunch = { app, view ->
            SpeedApp.instance.appRepository.launch(app, view)
        }
        binding.dock.onAppLongPress = { app, _ ->
            onAppLongPressOnHome?.invoke(app)
        }
        binding.iconGrid.setLayout(layoutStore.load())
        binding.dock.setLayout(layoutStore.loadDock())
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

    /**
     * Riceviamo i touch SOLO su area non-figlio. Quando l'utente tocca un'icona,
     * il GridLayout li gestisce e noi non vediamo nulla.
     */
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
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
