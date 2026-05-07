package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
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

class HomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewHomeBinding =
        ViewHomeBinding.inflate(LayoutInflater.from(context), this)

    private val layoutStore = HomeLayoutStore(context)
    private val settings = SpeedApp.instance.settingsRepository

    private val pages = mutableListOf<IconGridView>()
    private val pageCount = 2

    var onSwipeUp: (() -> Unit)? = null
    var onSearchTap: (() -> Unit)? = null
    var onHomeLongPress: (() -> Unit)? = null
    var onAppLongPressOnHome: ((AppInfo) -> Unit)? = null

    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeTrackingActive = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // Soglia minima del fling verticale per considerarlo swipe-up (px/s di velocità o distanza)
    private val swipeMinDistance = resources.displayMetrics.density * 80f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onHomeLongPress?.invoke()
        }
    })

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(180)
        }

        binding.searchBar.setOnClickListener { onSearchTap?.invoke() }

        // Costruisci le pagine
        repeat(pageCount) { idx ->
            val page = IconGridView(context).apply {
                pageIndex = idx
                onAppLaunch = { app, view ->
                    SpeedApp.instance.appRepository.launch(app, view)
                }
                onAppLongPress = { app, _ ->
                    onAppLongPressOnHome?.invoke(app)
                }
                setLayout(layoutStore.loadPage(idx))
            }
            pages.add(page)
            binding.pagedHome.addPage(page)
        }

        binding.pageIndicator.setPages(pageCount, 0)
        binding.pagedHome.onPageChanged = { p ->
            binding.pageIndicator.setPages(pageCount, p)
        }

        SpeedApp.instance.dragHandler = { origin, key, target ->
            handleDrag(origin, key, target)
        }

        applySettings()
    }

    private fun handleDrag(origin: String, key: String, target: String) {
        val app = SpeedApp.instance.appRepository.apps.value?.find { it.key == key } ?: return
        // Format target: "grid{N}:idx"
        val regex = Regex("""grid(\d+):(\d+)""")
        val match = regex.matchEntire(target) ?: return
        val targetPage = match.groupValues[1].toInt()
        val targetIdx = match.groupValues[2].toInt()
        val targetGrid = pages.getOrNull(targetPage) ?: return

        // Format origin: "grid{N}:idx"
        val originMatch = regex.matchEntire(origin)
        if (originMatch != null) {
            val originPage = originMatch.groupValues[1].toInt()
            if (originPage != targetPage) {
                // Cross-page: rimuovi dalla pagina d'origine, aggiungi alla destinazione
                pages.getOrNull(originPage)?.unpinApp(app)
                targetGrid.swapWith(key, targetIdx)
                return
            }
        }
        targetGrid.swapWith(key, targetIdx)
    }

    private fun applySettings() {
        binding.widgetSlot.visibility = if (settings.showWidgetSlot.value == true) View.VISIBLE else View.GONE
        binding.searchBar.visibility = if (settings.showSearchBar.value == true) View.VISIBLE else View.GONE
    }

    fun reapplySettings() {
        applySettings()
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 4
        for (page in pages) page.applyGridSize(cols, rows)
    }

    /**
     * v10: rilevamento swipe-up forzato che sovrasta i figli.
     * Se vediamo un movimento dal basso verso l'alto sufficientemente forte,
     * lo intercettiamo a livello FrameLayout e apriamo il drawer.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = ev.x
                swipeDownY = ev.y
                swipeTrackingActive = true
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeTrackingActive) return false
                val dx = abs(ev.x - swipeDownX)
                val dy = swipeDownY - ev.y
                // Swipe-up: dy positivo grande, dy >> dx
                if (dy > swipeMinDistance && dy > dx * 1.5f) {
                    swipeTrackingActive = false
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSwipeUp?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeTrackingActive = false
            }
        }
        return false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    fun attachWidgetHost(host: WidgetHostController) {
        binding.widgetSlot.setHostController(host)
    }

    fun refreshApps(apps: List<AppInfo>) {
        for (page in pages) page.refresh(apps)
    }

    fun refreshDots() {
        for (page in pages) page.invalidate()
    }

    fun pinApp(app: AppInfo) = pages.firstOrNull()?.pinApp(app) ?: false
    fun unpinApp(app: AppInfo) {
        for (page in pages) page.unpinApp(app)
    }
    fun isPinned(app: AppInfo) = pages.any { it.isPinned(app) }
}
