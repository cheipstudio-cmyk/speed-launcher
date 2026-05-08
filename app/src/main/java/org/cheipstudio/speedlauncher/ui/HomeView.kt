package org.cheipstudio.speedlauncher.ui

import android.animation.LayoutTransition
import android.app.Activity
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
import org.cheipstudio.speedlauncher.data.HomeItem
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
    /** v18: double tap su searchbar = blocca schermo */
    var onLockScreen: (() -> Unit)? = null

    private var trackStartX = 0f
    private var trackStartY = 0f
    private var tracking = false

    /** v26: overlay per fade durante swipe up del drawer */
    private val fadeOverlay = android.view.View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = false
        isFocusable = false
        // v26: non intercetta tap finché alpha > 0 (e anche allora è solo decorativo)
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )
    }
    // v18: threshold più reattivo (45dp invece di 60dp)
    private val swipeThreshold = resources.displayMetrics.density * 35f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            // v26: niente haptic qui - lo facciamo SOLO in onInterceptTouchEvent quando il threshold è confermato
            if (vy < -500f && abs(vy) > abs(vx) * 1.0f) {
                onSwipeUp?.invoke()
                return true
            }
            if (settings.swipeDownNotifications.value == true &&
                vy > 500f && abs(vy) > abs(vx) * 1.0f) {
                StatusBarHelper.expandNotifications(context)
                return true
            }
            return false
        }
    })

    /** v18: doppio tap detector sulla searchbar */
    private val searchBarDoubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            handleSearchTap()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (settings.doubleTapLock.value == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val act = context as? Activity
                if (act != null) ScreenLockHelper.lockScreen(act)
                return true
            }
            return false
        }
    })

    init {
        // v26: aggiungo overlay per fade al drawer
        addView(fadeOverlay)

        // v32: setup callback per entrambe le RecommendedView (top + bottom)
        val onRecClick: (org.cheipstudio.speedlauncher.data.AppInfo) -> Unit = { app ->
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
                SpeedApp.instance.usageTracker.recordLaunch(app.key)
            } catch (_: Throwable) {}
            postDelayed({ refreshRecommended() }, 500)
        }
        val onRecLong: (org.cheipstudio.speedlauncher.data.AppInfo) -> Unit = { app ->
            onAppMenuRequest?.invoke(app)
        }
        binding.recommendedRow.onAppClick = onRecClick
        binding.recommendedRow.onAppLongPress = onRecLong
        binding.recommendedRowBottom.onAppClick = onRecClick
        binding.recommendedRowBottom.onAppLongPress = onRecLong

        // v18: animazione layout dipende dallo stile selezionato
        applyAnimationStyle()
        // v20: gestione 3 bottoni nav bar - aggiungo solo il bottom inset al container interno
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Trova il primo LinearLayout figlio (il container di view_home)
            val child = (0 until childCount).firstNotNullOfOrNull {
                getChildAt(it) as? android.widget.LinearLayout
            }
            child?.let { ll ->
                val basePadTop = ll.paddingTop
                val basePadBottom = ll.paddingBottom
                // applica una sola volta usando tag
                if (ll.tag != "insets-applied") {
                    ll.setPadding(
                        ll.paddingLeft,
                        basePadTop + bars.top,
                        ll.paddingRight,
                        basePadBottom + bars.bottom
                    )
                    ll.tag = "insets-applied"
                }
            }
            insets
        }

        binding.searchBar.setOnTouchListener { _, ev ->
            searchBarDoubleTapDetector.onTouchEvent(ev)
            true
        }
        binding.btnHomeMenu.setOnClickListener { onHomeLongPress?.invoke() }

        updateSearchBarText()
        applySearchBarStyle()

        val maxPageInData = layoutStore.load().maxOfOrNull { it.page } ?: 0
        val initialPageCount = (maxPageInData + 1).coerceAtLeast(1)
        repeat(initialPageCount) { idx -> addPageAt(idx) }
        updatePageIndicator()

        binding.pagedHome.onPageChanged = { _ -> updatePageIndicator() }
        binding.pageIndicator.onPageTap = { idx -> binding.pagedHome.snapToPage(idx, true) }

        SpeedApp.instance.dragHandler = { origin, key, target -> handleDrag(origin, key, target) }

        applySettings()
    }

    private fun applyAnimationStyle() {
        when (settings.animationStyle.value) {
            SettingsRepository.ANIM_NONE -> {
                layoutTransition = null
            }
            SettingsRepository.ANIM_STANDARD -> {
                layoutTransition = LayoutTransition().apply {
                    enableTransitionType(LayoutTransition.CHANGING)
                    setDuration(80)
                }
            }
            else -> {
                // ANIM_EXPRESSIVE: più espressivo ma comunque rapido
                layoutTransition = LayoutTransition().apply {
                    enableTransitionType(LayoutTransition.CHANGING)
                    setDuration(120)
                }
            }
        }
    }

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
            onFolderOpen = { folder -> openFolder(folder) }
            setLayout(layoutStore.loadPage(idx))
        }
        pages.add(page)
        binding.pagedHome.addPage(page)
    }

    private fun ensurePageExists(idx: Int) {
        while (pages.size <= idx) addPageAt(pages.size)
        updatePageIndicator()
    }

    private fun trimEmptyPages() {
        while (pages.size > 1 && pages.last().isEmpty()) {
            pages.removeAt(pages.size - 1)
            binding.pagedHome.removePage(pages.size)
            layoutStore.savePage(pages.size, emptyList())
            if (binding.pagedHome.currentPage >= pages.size) {
                binding.pagedHome.snapToPage(pages.size - 1, true)
            }
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
        if (pages[lastIdx].isFull()) ensurePageExists(lastIdx + 1)
    }

    private fun handleDrag(origin: String, key: String, target: String) {
        val regex = Regex("""grid(\d+):(\d+)""")
        val matchT = regex.matchEntire(target) ?: return
        val targetPage = matchT.groupValues[1].toInt()
        val targetIdx = matchT.groupValues[2].toInt()
        ensurePageExists(targetPage)
        val targetGrid = pages.getOrNull(targetPage) ?: return

        val matchO = regex.matchEntire(origin)
        val fromGrid = if (matchO != null) {
            pages.getOrNull(matchO.groupValues[1].toInt())
        } else null
        val fromIdx = if (matchO != null) matchO.groupValues[2].toInt() else -1

        targetGrid.handleIncomingDrop(key, fromGrid, fromIdx, targetIdx)
        maybeCreateNextPage()
        post { trimEmptyPages() }
    }

    private fun openFolder(folder: HomeItem) {
        FolderSheet.show(
            context = context,
            folder = folder,
            onLaunch = { app -> SpeedApp.instance.appRepository.launch(app, this) },
            onRename = { newName ->
                pages.forEach { grid ->
                    grid.updateFolder(folder.key) { f -> f.copy(name = newName) }
                }
            },
            onRemoveFromFolder = { app ->
                pages.forEach { grid ->
                    grid.updateFolder(folder.key) { f ->
                        val newApps = f.folderApps - app.key
                        when {
                            newApps.isEmpty() -> null
                            newApps.size == 1 -> HomeItem(
                                key = newApps[0], page = f.page,
                                cellX = f.cellX, cellY = f.cellY,
                                type = HomeItem.TYPE_APP
                            )
                            else -> f.copy(folderApps = newApps)
                        }
                    }
                }
                val appInfo = SpeedApp.instance.appRepository.apps.value?.find { it.key == app.key }
                if (appInfo != null && pages.none { it.isPinned(appInfo) }) {
                    pinApp(appInfo)
                }
            },
            onDeleteFolder = {
                pages.forEach { grid -> grid.updateFolder(folder.key) { null } }
                post { trimEmptyPages() }
            }
        )
    }

    private fun applySettings() {
        binding.widgetSlot.visibility = if (settings.showWidgetSlot.value == true) View.VISIBLE else View.GONE
        binding.searchBar.visibility = View.VISIBLE
        updateSearchBarText()
        applySearchBarStyle()
        applyAnimationStyle()
    }

    fun reapplySettings() {
        refreshRecommended()
        applySettings()
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 4
        for (page in pages) {
            page.applyGridSize(cols, rows)
            // refresh icone per nuova forma/colore dot
            SpeedApp.instance.appRepository.apps.value?.let { page.refresh(it) }
        }
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

                // v26: durante lo swipe up, aggiorna alpha overlay proporzionale
                if (dy < 0 && abs(dy) > dx * 1.0f) {
                    val maxFadeDistance = height * 0.45f
                    val progress = (abs(dy) / maxFadeDistance).coerceIn(0f, 1f)
                    fadeOverlay.alpha = progress * 0.55f
                }

                if (dy < -swipeThreshold && abs(dy) > dx * 1.0f) {
                    tracking = false
                    performHapticFeedbackLight()
                    // mantieni overlay visibile finché il drawer non è aperto
                    onSwipeUp?.invoke()
                    // dissolvi overlay leggermente in ritardo
                    fadeOverlay.animate().alpha(0f).setDuration(220).start()
                    return true
                }
                if (settings.swipeDownNotifications.value == true &&
                    dy > swipeThreshold && abs(dy) > dx * 1.0f) {
                    tracking = false
                    performHapticFeedbackLight()
                    StatusBarHelper.expandNotifications(context)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                gestureDetector.onTouchEvent(ev)
                // v26: se overlay parzialmente visibile, dissolvilo
                if (fadeOverlay.alpha > 0f) {
                    fadeOverlay.animate().alpha(0f).setDuration(180).start()
                }
            }
        }
        return false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    fun attachWidgetHost(host: WidgetHostController) { binding.widgetSlot.setHostController(host) }
    fun refreshApps(apps: List<AppInfo>) {
        for (page in pages) page.refresh(apps)
        maybeCreateNextPage()
        refreshRecommended()
    }

    /** v32: aggiorna la sezione Raccomandate se AI mode è attivo + posizione top/bottom */
    fun refreshRecommended() {
        val aiOn = settings.aiLauncherMode.value == true
        val pos = settings.recommendedPosition.value ?: org.cheipstudio.speedlauncher.data.SettingsRepository.REC_POS_TOP
        if (!aiOn) {
            binding.recommendedRow.visibility = android.view.View.GONE
            binding.recommendedRowBottom.visibility = android.view.View.GONE
            return
        }
        if (pos == org.cheipstudio.speedlauncher.data.SettingsRepository.REC_POS_BOTTOM) {
            binding.recommendedRow.visibility = android.view.View.GONE
            binding.recommendedRowBottom.visibility = android.view.View.VISIBLE
            binding.recommendedRowBottom.refresh("home")
        } else {
            binding.recommendedRow.visibility = android.view.View.VISIBLE
            binding.recommendedRowBottom.visibility = android.view.View.GONE
            binding.recommendedRow.refresh("home")
        }
    }
    fun refreshDots() { for (page in pages) page.invalidate() }
    fun pinApp(app: AppInfo): Boolean {
        for (page in pages) if (page.pinApp(app)) { maybeCreateNextPage(); return true }
        ensurePageExists(pages.size)
        return pages.last().pinApp(app)
    }
    fun unpinApp(app: AppInfo) {
        for (page in pages) page.unpinApp(app)
        post { trimEmptyPages() }
    }
    fun isPinned(app: AppInfo) = pages.any { it.isPinned(app) }

    /** v38: imposta l'alpha del dim overlay (0..1) */
    fun setDimOverlayAlpha(alpha: Float) {
        binding.dimOverlay.alpha = alpha.coerceIn(0f, 1f)
    }

    /**
     * v38: collega il scroll delle pagine al wallpaper offsets di Android.
     * Effetto: il wallpaper si muove leggermente quando cambi pagina, parallax classico.
     */
    fun attachWallpaperParallax(window: android.view.Window) {
        val wm = android.app.WallpaperManager.getInstance(context)
        val previous = binding.pagedHome.onPageChanged
        binding.pagedHome.onPageChanged = { page ->
            previous?.invoke(page)
            val parallaxOn = settings.wallpaperParallax.value == true
            if (parallaxOn) {
                val pageCount = binding.pagedHome.pageCount
                if (pageCount > 1) {
                    val xOffset = page.toFloat() / (pageCount - 1).toFloat()
                    try {
                        wm.setWallpaperOffsets(window.decorView.windowToken, xOffset.coerceIn(0f, 1f), 0f)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    /** v27: chiamato da MainActivity quando si preme home dalla home */
    fun snapToFirstPage() {
        if (binding.pagedHome.currentPage > 0) {
            binding.pagedHome.snapToPage(0, animate = true)
        }
    }

    /**
     * v26: haptic leggero, single-tap. Su API 30+ usa GESTURE_START (più sottile),
     * altrimenti CLOCK_TICK (era KEYBOARD_TAP — troppo forte).
     */
    private fun performHapticFeedbackLight() {
        if (settings.hapticEnabled.value != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
