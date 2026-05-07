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

    var onSwipeUp: (() -> Unit)? = null
    var onSearchTap: (() -> Unit)? = null
    var onHomeLongPress: (() -> Unit)? = null

    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeTrackingActive = false
    private val swipeMinDistance = resources.displayMetrics.density * 100f

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

        // Bottone settings nella search bar - punto di accesso AFFIDABILE al menu home
        binding.btnHomeMenu.setOnClickListener {
            onHomeLongPress?.invoke()
        }

        // Pagine dinamiche
        val maxPageInData = layoutStore.load().maxOfOrNull { it.page } ?: 0
        val initialPageCount = (maxPageInData + 1).coerceAtLeast(1)
        repeat(initialPageCount) { idx -> addPageAt(idx) }
        updatePageIndicator()

        binding.pagedHome.onPageChanged = { _ ->
            updatePageIndicator()
        }

        SpeedApp.instance.dragHandler = { origin, key, target ->
            handleDrag(origin, key, target)
        }

        applySettings()
    }

    private fun addPageAt(idx: Int) {
        val page = IconGridView(context).apply {
            pageIndex = idx
            onAppLaunch = { app, view ->
                SpeedApp.instance.appRepository.launch(app, view)
            }
            setLayout(layoutStore.loadPage(idx))
        }
        pages.add(page)
        binding.pagedHome.addPage(page)
    }

    private fun ensurePageExists(idx: Int) {
        while (pages.size <= idx) {
            addPageAt(pages.size)
        }
        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        binding.pageIndicator.setPages(pages.size, binding.pagedHome.currentPage.coerceAtMost(pages.size - 1))
        binding.pageIndicator.visibility = if (pages.size > 1) View.VISIBLE else View.INVISIBLE
    }

    private fun maybeCreateNextPage() {
        val lastIdx = pages.size - 1
        if (lastIdx < 0) return
        if (pages[lastIdx].isFull()) {
            ensurePageExists(lastIdx + 1)
        }
    }

    private fun handleDrag(origin: String, key: String, target: String) {
        val app = SpeedApp.instance.appRepository.apps.value?.find { it.key == key } ?: return
        val regex = Regex("""grid(\d+):(\d+)""")
        val match = regex.matchEntire(target) ?: return
        val targetPage = match.groupValues[1].toInt()
        val targetIdx = match.groupValues[2].toInt()

        ensurePageExists(targetPage)
        val targetGrid = pages.getOrNull(targetPage) ?: return

        val originMatch = regex.matchEntire(origin)
        if (originMatch != null) {
            val originPage = originMatch.groupValues[1].toInt()
            if (originPage != targetPage) {
                pages.getOrNull(originPage)?.unpinApp(app)
                targetGrid.swapWith(key, targetIdx)
                maybeCreateNextPage()
                return
            }
        }
        targetGrid.swapWith(key, targetIdx)
        maybeCreateNextPage()
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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Se sopra di noi c'è un dialog/bottomsheet, non intercettiamo nulla
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            for (frag in activity.supportFragmentManager.fragments) {
                if (frag is androidx.fragment.app.DialogFragment && frag.isVisible) return false
            }
        }

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
                if (dy > swipeMinDistance && dy > dx * 2f) {
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
        maybeCreateNextPage()
    }

    fun refreshDots() {
        for (page in pages) page.invalidate()
    }

    fun pinApp(app: AppInfo): Boolean {
        for (page in pages) {
            if (page.pinApp(app)) {
                maybeCreateNextPage()
                return true
            }
        }
        ensurePageExists(pages.size)
        return pages.last().pinApp(app)
    }

    fun unpinApp(app: AppInfo) {
        for (page in pages) page.unpinApp(app)
    }

    fun isPinned(app: AppInfo) = pages.any { it.isPinned(app) }
}
