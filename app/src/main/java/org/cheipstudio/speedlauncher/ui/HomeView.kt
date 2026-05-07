package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.app.SearchManager
import android.content.Context
import android.content.Intent
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
import org.cheipstudio.speedlauncher.data.SettingsRepository
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
    var onAppMenuRequest: ((AppInfo) -> Unit)? = null

    private var trackStartX = 0f
    private var trackStartY = 0f
    private var trackingSwipe = false
    private val swipeThreshold = resources.displayMetrics.density * 80f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onLongPress(e: MotionEvent) {
            // v13: se stiamo sopra un'icona o widget, NON aprire il menu home
            if (isOverIconOrWidget(e.x, e.y)) return
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onHomeLongPress?.invoke()
        }
        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            if (velocityY < -1200f && abs(velocityY) > abs(velocityX) * 1.5f) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onSwipeUp?.invoke()
                return true
            }
            return false
        }
    })

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(180)
        }

        binding.searchBar.setOnClickListener { handleSearchTap() }
        binding.btnHomeMenu.setOnClickListener { onHomeLongPress?.invoke() }

        // v13: aggiorna testo search bar in base al mode
        updateSearchBarText()

        val maxPageInData = layoutStore.load().maxOfOrNull { it.page } ?: 0
        val initialPageCount = (maxPageInData + 1).coerceAtLeast(1)
        repeat(initialPageCount) { idx -> addPageAt(idx) }
        updatePageIndicator()

        binding.pagedHome.onPageChanged = { _ -> updatePageIndicator() }

        SpeedApp.instance.dragHandler = { origin, key, target ->
            handleDrag(origin, key, target)
        }

        applySettings()
    }

    private fun updateSearchBarText() {
        val text = if (settings.searchMode.value == SettingsRepository.MODE_GOOGLE)
            context.getString(org.cheipstudio.speedlauncher.R.string.search_web)
        else
            context.getString(org.cheipstudio.speedlauncher.R.string.search_apps)
        binding.searchBarHint.text = text
    }

    /**
     * v13: hit-test contro griglia + widget slot, per impedire long-press home
     * sopra a un elemento che ha già la sua gestione del long-press.
     */
    private fun isOverIconOrWidget(x: Float, y: Float): Boolean {
        // Widget slot
        if (binding.widgetSlot.visibility == View.VISIBLE) {
            if (hitTest(binding.widgetSlot, x, y)) return true
        }
        // Pager (qualsiasi pagina ha icone, blocchiamo tutta l'area)
        if (hitTest(binding.pagedHome, x, y)) return true
        return false
    }

    private fun hitTest(view: View, x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val myLoc = IntArray(2)
        getLocationInWindow(myLoc)
        val left = loc[0] - myLoc[0]
        val top = loc[1] - myLoc[1]
        return x >= left && x <= left + view.width && y >= top && y <= top + view.height
    }

    private fun handleSearchTap() {
        when (settings.searchMode.value) {
            SettingsRepository.MODE_GOOGLE -> {
                try {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(SearchManager.QUERY, "")
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    onSearchTap?.invoke()
                }
            }
            else -> onSearchTap?.invoke()
        }
    }

    private fun addPageAt(idx: Int) {
        val page = IconGridView(context).apply {
            pageIndex = idx
            onAppLaunch = { app, view ->
                SpeedApp.instance.appRepository.launch(app, view)
            }
            onAppLongPress = { app, _ ->
                onAppMenuRequest?.invoke(app)
            }
            setLayout(layoutStore.loadPage(idx))
        }
        pages.add(page)
        binding.pagedHome.addPage(page)
    }

    private fun ensurePageExists(idx: Int) {
        while (pages.size <= idx) addPageAt(pages.size)
        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        binding.pageIndicator.setPages(pages.size, binding.pagedHome.currentPage.coerceAtMost(pages.size - 1))
        binding.pageIndicator.visibility = if (pages.size > 1) View.VISIBLE else View.INVISIBLE
    }

    private fun maybeCreateNextPage() {
        val lastIdx = pages.size - 1
        if (lastIdx < 0) return
        if (pages[lastIdx].isFull()) ensurePageExists(lastIdx + 1)
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
        updateSearchBarText()
    }

    fun reapplySettings() {
        applySettings()
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 4
        for (page in pages) page.applyGridSize(cols, rows)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            for (frag in activity.supportFragmentManager.fragments) {
                if (frag is androidx.fragment.app.DialogFragment && frag.isVisible) return false
            }
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                trackStartX = ev.x
                trackStartY = ev.y
                trackingSwipe = true
                gestureDetector.onTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!trackingSwipe) return false
                val dx = abs(ev.x - trackStartX)
                val dy = trackStartY - ev.y
                gestureDetector.onTouchEvent(ev)
                if (dy > swipeThreshold && dy > dx * 1.5f) {
                    trackingSwipe = false
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSwipeUp?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingSwipe = false
                gestureDetector.onTouchEvent(ev)
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

    fun refreshDots() { for (page in pages) page.invalidate() }

    fun pinApp(app: AppInfo): Boolean {
        for (page in pages) {
            if (page.pinApp(app)) { maybeCreateNextPage(); return true }
        }
        ensurePageExists(pages.size)
        return pages.last().pinApp(app)
    }

    fun unpinApp(app: AppInfo) { for (page in pages) page.unpinApp(app) }
    fun isPinned(app: AppInfo) = pages.any { it.isPinned(app) }
}
