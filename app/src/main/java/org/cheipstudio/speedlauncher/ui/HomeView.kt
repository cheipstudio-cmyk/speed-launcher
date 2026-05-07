package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.card.MaterialCardView
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
    private var tracking = false
    private val swipeThreshold = resources.displayMetrics.density * 70f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (vy < -1200f && abs(vy) > abs(vx) * 1.4f) {
                // v15: vibrazione più leggera (KEYBOARD_TAP invece di CONTEXT_CLICK)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSwipeUp?.invoke()
                return true
            }
            return false
        }
    })

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(160)
        }

        // v15.1: rispetta status bar in alto + nav bar in basso (3-button non copre più la search bar)
        clipToPadding = false
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        binding.searchBar.setOnClickListener { handleSearchTap() }
        binding.btnHomeMenu.setOnClickListener { onHomeLongPress?.invoke() }

        updateSearchBarText()
        applySearchBarStyle()

        val maxPageInData = layoutStore.load().maxOfOrNull { it.page } ?: 0
        val initialPageCount = (maxPageInData + 1).coerceAtLeast(1)
        repeat(initialPageCount) { idx -> addPageAt(idx) }
        updatePageIndicator()

        binding.pagedHome.onPageChanged = { _ -> updatePageIndicator() }

        SpeedApp.instance.dragHandler = { origin, key, target -> handleDrag(origin, key, target) }

        applySettings()
    }

    /** v15: applica lo stile della barra di ricerca scelto nei settings */
    private fun applySearchBarStyle() {
        val card = binding.searchBar as MaterialCardView
        val hint = binding.searchBarHint
        val searchIcon = binding.searchIcon
        val menuIcon = binding.btnHomeMenu

        when (settings.searchBarStyle.value) {
            SettingsRepository.STYLE_TRANSPARENT -> {
                card.setCardBackgroundColor(Color.parseColor("#33FFFFFF"))
                card.cardElevation = 0f
                hint.setTextColor(Color.parseColor("#DDFFFFFF"))
                searchIcon.setColorFilter(Color.parseColor("#DDFFFFFF"))
                menuIcon.setColorFilter(Color.parseColor("#DDFFFFFF"))
            }
            SettingsRepository.STYLE_DARK -> {
                card.setCardBackgroundColor(Color.parseColor("#1F1F1F"))
                card.cardElevation = 2 * resources.displayMetrics.density
                hint.setTextColor(Color.parseColor("#CCFFFFFF"))
                searchIcon.setColorFilter(Color.parseColor("#CCFFFFFF"))
                menuIcon.setColorFilter(Color.parseColor("#CCFFFFFF"))
            }
            SettingsRepository.STYLE_LIGHT -> {
                card.setCardBackgroundColor(Color.parseColor("#F0F0F0"))
                card.cardElevation = 2 * resources.displayMetrics.density
                hint.setTextColor(Color.parseColor("#333333"))
                searchIcon.setColorFilter(Color.parseColor("#666666"))
                menuIcon.setColorFilter(Color.parseColor("#666666"))
            }
            else -> {
                card.setCardBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh))
                card.cardElevation = 2 * resources.displayMetrics.density
                hint.setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                searchIcon.setColorFilter(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                menuIcon.setColorFilter(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun updateSearchBarText() {
        val text = if (settings.searchMode.value == SettingsRepository.MODE_GOOGLE)
            context.getString(org.cheipstudio.speedlauncher.R.string.search_web)
        else
            context.getString(org.cheipstudio.speedlauncher.R.string.search_apps)
        binding.searchBarHint.text = text
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
                } catch (_: Throwable) { onSearchTap?.invoke() }
            }
            else -> onSearchTap?.invoke()
        }
    }

    private fun addPageAt(idx: Int) {
        val page = IconGridView(context).apply {
            pageIndex = idx
            onAppLaunch = { app, view -> SpeedApp.instance.appRepository.launch(app, view) }
            onAppLongPress = { app, _ -> onAppMenuRequest?.invoke(app) }
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
        // v15: search bar SEMPRE visibile
        binding.searchBar.visibility = View.VISIBLE
        updateSearchBarText()
        applySearchBarStyle()
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
                trackStartX = ev.x; trackStartY = ev.y
                tracking = true
                gestureDetector.onTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = abs(ev.x - trackStartX)
                val dy = ev.y - trackStartY
                gestureDetector.onTouchEvent(ev)
                if (dy < -swipeThreshold && abs(dy) > dx * 1.4f) {
                    tracking = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSwipeUp?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                gestureDetector.onTouchEvent(ev)
            }
        }
        return false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    fun attachWidgetHost(host: WidgetHostController) { binding.widgetSlot.setHostController(host) }
    fun refreshApps(apps: List<AppInfo>) { for (page in pages) page.refresh(apps); maybeCreateNextPage() }
    fun refreshDots() { for (page in pages) page.invalidate() }
    fun pinApp(app: AppInfo): Boolean {
        for (page in pages) if (page.pinApp(app)) { maybeCreateNextPage(); return true }
        ensurePageExists(pages.size)
        return pages.last().pinApp(app)
    }
    fun unpinApp(app: AppInfo) { for (page in pages) page.unpinApp(app) }
    fun isPinned(app: AppInfo) = pages.any { it.isPinned(app) }
}
