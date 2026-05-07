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

class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewHomeBinding =
        ViewHomeBinding.inflate(LayoutInflater.from(context), this)

    private val layoutStore = HomeLayoutStore(context)
    private val settings = SpeedApp.instance.settingsRepository

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
            if (velocityY < -1500f && abs(velocityY) > abs(velocityX) * 1.3f) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onSwipeUp?.invoke()
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onHomeLongPress?.invoke()
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

        applySettings()
    }

    private fun applySettings() {
        binding.dock.visibility = if (settings.showDock.value == true) View.VISIBLE else View.GONE
        binding.widgetSlot.visibility = if (settings.showWidgetSlot.value == true) View.VISIBLE else View.GONE
    }

    fun reapplySettings() {
        applySettings()
        // Se la griglia è cambiata, applica
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 4
        binding.iconGrid.applyGridSize(cols, rows)
    }

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
