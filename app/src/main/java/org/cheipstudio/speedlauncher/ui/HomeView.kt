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
    /** v59: callback per tap sul button razzo memory cleaner */
    var onMemoryCleanerRequest: (() -> Unit)? = null
    /** v18: double tap su searchbar = blocca schermo */
    var onLockScreen: (() -> Unit)? = null

    private var trackStartX = 0f
    private var trackStartY = 0f
    private var tracking = false
    /** v77: dedup vibrazione swipe per evitare doppi fire dello stesso gesto */
    private var swipeFireVibrated = false
    // v46: velocity tracker per swipe rapido
    private var swipeVelocityTracker: android.view.VelocityTracker? = null
    private val swipeFastVelocity = resources.displayMetrics.density * 800f  // 800dp/s

    /** v26: overlay per fade durante swipe up del drawer */
    private val fadeOverlay = android.view.View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = false
        isFocusable = false
        // v26: non intercetta tap finché alpha > 0 (e anche allora è solo decorativo)
        // v131: hardware layer per animazione alpha più fluida durante swipe
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )
    }
    // v18: threshold più reattivo (45dp invece di 60dp)
    private val swipeThreshold = resources.displayMetrics.density * 22f  // v46: più sensibile

    /** v140: callback per swipe destra dal bordo sinistro → apri pannello RSS */
    var onSwipeRightFromLeftEdge: (() -> Unit)? = null
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // v120+v144: consumo onDown se almeno un gesto è attivo (drawer / swipeDown / RSS panel)
            val drawerOn = settings.drawerEnabled.value != false
            val swipeDownOn = settings.swipeDownNotifications.value == true
            val rssPanelOn = settings.rssPanelEnabled.value == true
            return drawerOn || swipeDownOn || rssPanelOn
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            // v77: dedup vibrazione — usa flag swipeFireVibrated, una sola vibrazione per gesto.
            if (vy < -500f && abs(vy) > abs(vx) * 1.0f) {
                // v254: su RSS leading page, swipe up NON apre il drawer (deve scrollare il feed)
                if (isRssOverlayOpen) return false
                // v85: rispetta drawerEnabled
                if (settings.drawerEnabled.value != false) {
                    if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                    onSwipeUp?.invoke()
                    return true
                }
                return false
            }
            if (settings.swipeDownNotifications.value == true &&
                vy > 500f && abs(vy) > abs(vx) * 1.0f) {
                // v256: su RSS leading page, swipe down NON apre notifiche (deve scrollare feed)
                if (isRssOverlayOpen) return false
                if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
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
                HapticHelper.longPress(null)
                val act = context as? Activity
                if (act != null) ScreenLockHelper.lockScreen(act)
                return true
            }
            return false
        }
    })

    init {
        // v116: observer DIRETTO su drawerEnabled per garantire refresh barra
        // (il fix in MainActivity può non funzionare se HomeView è ricreata prima dell'observe)
        SpeedApp.instance.settingsRepository.drawerEnabled.observeForever {
            // Aggiorno barra quando cambia drawer
            try { applySearchBarVisibility() } catch (_: Throwable) {}
        }
        
        // v132: observer iconShape — refresh completo tutte le icone home + dock raccomandate
        SpeedApp.instance.settingsRepository.iconShape.observeForever {
            try {
                val allApps = SpeedApp.instance.appRepository.apps.value ?: return@observeForever
                pages.forEach { grid -> grid.refresh(allApps) }
                binding.recommendedRow.refresh(if (binding.recommendedRow.visibility == android.view.View.VISIBLE) "home" else "drawer")
                binding.recommendedRowBottom.refresh("home")
            } catch (_: Throwable) {}
        }
        // v139: observer showHomeLabels — refresh icone home
        SpeedApp.instance.settingsRepository.showHomeLabels.observeForever {
            try {
                val allApps = SpeedApp.instance.appRepository.apps.value ?: return@observeForever
                pages.forEach { grid -> grid.refresh(allApps) }
            } catch (_: Throwable) {}
        }

        // v88: wire auto-add per nuove app installate
        SpeedApp.instance.appRepository.onNewPackageInstalled = { appKey ->
            if (settings.autoAddNewApps.value == true) {
                val store = org.cheipstudio.speedlauncher.data.HomeLayoutStore(context)
                val cols = settings.gridCols.value ?: 4
                val rows = settings.gridRows.value ?: 5
                val added = org.cheipstudio.speedlauncher.tools.HomeAutoPopulator
                    .addSingleApp(store, appKey, cols, rows)
                if (added) {
                    // Reload pages dal disk
                    post { reloadAllPagesFromStore() }
                }
            }
        }

        // v26: aggiungo overlay per fade al drawer
        addView(fadeOverlay)

        // v32: setup callback per entrambe le RecommendedView (top + bottom)
        val onRecClick: (org.cheipstudio.speedlauncher.data.AppInfo) -> Unit = { app ->
            // v160: usa AppRepository.launch per beneficiare dell'animazione di apertura Pixel-style
            try {
                SpeedApp.instance.appRepository.launch(app, binding.recommendedRow)
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
        // v226: long press sul container dock apre DockResizeSheet
        val dockLongPress = {
            try {
                val ctx = context as? android.content.Context
                val act = ctx as? androidx.fragment.app.FragmentActivity
                if (act != null) {
                    HapticHelper.longPress(this)
                    DockResizeSheet().apply {
                        onChanged = { refreshRecommended() }
                    }.show(act.supportFragmentManager, "dockResize")
                }
            } catch (_: Throwable) {}
        }
        binding.recommendedRow.onContainerLongPress = dockLongPress
        binding.recommendedRowBottom.onContainerLongPress = dockLongPress

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
                val basePadBottom = ll.paddingBottom
                // v83: top padding = SOLO status bar inset + 4dp di respiro.
                // Prima sommavamo home_top_padding del XML al bars.top → troppo spazio.
                // Ora il widget parte subito sotto la status bar.
                if (ll.tag != "insets-applied") {
                    val extraTopDp = (4 * resources.displayMetrics.density).toInt()
                    ll.setPadding(
                        ll.paddingLeft,
                        bars.top + extraTopDp,
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
        binding.btnHomeMenu.setOnClickListener { 
            if (!isRssOverlayOpen) onHomeLongPress?.invoke()  // v254: no menu su RSS
        }

        // v46: long press home robusto via GestureDetector (più affidabile di setOnLongClickListener su scroll view)

        updateSearchBarText()
        applySearchBarStyle()

        val maxPageInData = layoutStore.load().maxOfOrNull { it.page } ?: 0
        val initialPageCount = (maxPageInData + 1).coerceAtLeast(1)
        repeat(initialPageCount) { idx -> addPageAt(idx) }
        updatePageIndicator()
        (context as? androidx.lifecycle.LifecycleOwner)?.let { lo ->
            SpeedApp.instance.settingsRepository.searchMode.observe(lo) { updateSearchBarText() }
            SpeedApp.instance.settingsRepository.rssPanelEnabled.observe(lo) { enabled ->
                binding.rssEdgeIndicator.visibility = View.GONE
                applyRssOverlayEnabled(enabled == true)
            }
            // v227: observers dock per refresh real-time
            SpeedApp.instance.settingsRepository.recommendedPosition.observe(lo) { refreshRecommended() }
            SpeedApp.instance.settingsRepository.dockTheme.observe(lo) {
                binding.recommendedRow.refreshTheme()
                binding.recommendedRowBottom.refreshTheme()
            }
            // v228: nascondi drawerHandle se drawer disattivato
            SpeedApp.instance.settingsRepository.drawerEnabled.observe(lo) { enabled ->
                binding.drawerHandle.visibility = if (enabled == true) View.VISIBLE else View.GONE
            }
            // v243: real-time visibility spazio widget
            SpeedApp.instance.settingsRepository.showWidgetSlot.observe(lo) { show ->
                binding.widgetSlot.visibility = if (show == true) View.VISIBLE else View.GONE
                applyWidgetConfig()
            }
        }

        // v194: pillola laterale RSS sempre visibile + click
        // v250: bottone laterale RSS rimosso (sostituito da leading page swipe)
        binding.rssEdgeIndicator.visibility = View.GONE
        binding.rssEdgeIndicator.alpha = 1f
        binding.rssEdgeIndicator.isClickable = true
        binding.rssEdgeIndicator.setOnClickListener {
            performHapticFeedbackLight()
            // Push effect su tap
            binding.rssEdgeIndicator.animate()
                .scaleX(1.1f).scaleY(1.1f)
                .setDuration(80)
                .withEndAction {
                    binding.rssEdgeIndicator.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .start()
                }
                .start()
            // v245: trigger rimosso
        }


        binding.pagedHome.onPageChanged = { idx -> 
            updatePageIndicator()
            try { binding.widgetSlot.pageIndex = idx } catch (_: Throwable) {}
        }
        // v266: parallax pagine durante swipe orizzontale
        binding.pagedHome.onScrollFraction = { fraction ->
            try { applyPageParallax(fraction) } catch (_: Throwable) {}
        }
        binding.pageIndicator.onPageTap = { idx -> binding.pagedHome.snapToPage(idx, true) }
        // v261: tap dot RSS → apre overlay
        binding.pageIndicator.onLeadingTap = { openRssOverlay() }

        SpeedApp.instance.dragHandler = { origin, key, target -> handleDrag(origin, key, target) }

        applySettings()
    }

    private fun applyAnimationStyle() {
        when (settings.animationStyle.value) {
            SettingsRepository.ANIM_NONE -> {
                layoutTransition = null
            }
            SettingsRepository.ANIM_FAST -> {
                // v74: fast — animazioni minime per massima fluidità
                layoutTransition = LayoutTransition().apply {
                    enableTransitionType(LayoutTransition.CHANGING)
                    setDuration(40)
                }
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
        val text = when (settings.searchMode.value) {
            SettingsRepository.MODE_GOOGLE ->
                context.getString(org.cheipstudio.speedlauncher.R.string.search_web)
            SettingsRepository.MODE_UNIVERSAL ->
                context.getString(org.cheipstudio.speedlauncher.R.string.search_universal)
            else ->
                context.getString(org.cheipstudio.speedlauncher.R.string.search_apps)
        }
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
                } catch (_: Throwable) {
                    // v85: solo se drawer abilitato apre fallback
                    onSearchTap?.invoke()  // v251: sempre invoca, MainActivity decide cosa aprire
                }
            }
            else -> {
                onSearchTap?.invoke()  // v251: MainActivity decide
            }
        }
    }

    private fun addPageAt(idx: Int) {
        val page = IconGridView(context).apply {
            pageIndex = idx
            onAppLaunch = { app, view -> SpeedApp.instance.appRepository.launch(app, view) }
            onAppLongPress = { app, _ -> onAppMenuRequest?.invoke(app) }
            onFolderOpen = { folder -> openFolder(folder) }
            // v132: long press cartella → menu rinomina/elimina
            onFolderLongPress = { folder -> showFolderMenu(folder) }
            // v59: tap su button razzo memory cleaner → pulisce + snackbar
            onMemoryCleanerTap = { _ ->
                onMemoryCleanerRequest?.invoke()
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

    /** v43: aggiunge una pagina vuota in fondo */
    fun addEmptyPage() {
        addPageAt(pages.size)
        layoutStore.savePage(pages.size - 1, emptyList())
        updatePageIndicator()
    }

    /** v43: rimuove una pagina per indice (anche se non vuota — sposta il contenuto fuori, lo scarta) */
    fun forceRemovePageAt(idx: Int) {
        if (idx !in 0 until pages.size) return
        if (pages.size <= 1) return  // tieni almeno una pagina
        // sposta verso sinistra: re-salva tutte le pagine dopo idx con i nuovi indici
        pages.removeAt(idx)
        binding.pagedHome.removePage(idx)
        // Ricalcola e salva di nuovo gli indici di pagina (gli items hanno page=N che va decrementato)
        for (i in idx until pages.size) {
            pages[i].pageIndex = i
        }
        // Persisti: sposta tutte le pagine a partire da idx una posizione indietro
        val totalAfter = pages.size
        // Carica items, riassegna page=i e cellX/cellY, salva, ricarica nelle griglie
        val itemsByPage = mutableListOf<List<HomeItem>>()
        for (i in 0 until totalAfter) {
            val items = pages[i].getItems().map { it.copy(page = i) }
            itemsByPage.add(items)
        }
        for (i in 0 until totalAfter) {
            layoutStore.savePage(i, itemsByPage[i])
            pages[i].setLayout(itemsByPage[i])
        }
        // Cancello la pagina vecchia in coda (oltre totalAfter)
        layoutStore.savePage(totalAfter, emptyList())
        if (binding.pagedHome.currentPage >= totalAfter) {
            binding.pagedHome.snapToPage(totalAfter - 1, true)
        }
        updatePageIndicator()
    }

    /** v43: numero di pagine */
    fun getPageCount(): Int = pages.size

    /** v43: numero di icone su una pagina */
    fun getPageIconCount(idx: Int): Int =
        if (idx in 0 until pages.size) pages[idx].getItems().size else 0

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
        // v261: indicatore con dot RSS extra se overlay abilitato
        val rssEnabled = settings.rssPanelEnabled.value == true
        binding.pageIndicator.setPages(
            count = pages.size,
            current = binding.pagedHome.currentPage.coerceAtMost(pages.size - 1).coerceAtLeast(0),
            hasLeading = rssEnabled,
            leadingActive = isRssOverlayOpen
        )
        binding.pageIndicator.visibility = if (pages.size > 1 || rssEnabled) View.VISIBLE else View.INVISIBLE
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
        // v88+v120: snap alla pagina di destinazione + refresh COMPLETO
        // (icona spariva visivamente fino a quando si tornava alla home)
        post {
            trimEmptyPages()
            updatePageIndicator()
            if (binding.pagedHome.currentPage != targetPage) {
                binding.pagedHome.snapToPage(targetPage, true)
            }
            // v120: refresh completo apps su TUTTE le pagine — non solo invalidate
            // perché l'icona droppata in pagina vuota non comparirà finché non
            // chiamiamo refresh con la lista delle app.
            val allApps = SpeedApp.instance.appRepository.apps.value
            if (allApps != null) {
                pages.forEach { grid -> grid.refresh(allApps) }
            }
            targetGrid.invalidate()
            targetGrid.requestLayout()
        }
    }

    private fun openFolder(folder: HomeItem) {
        FolderSheet.show(
            context = context,
            folder = folder,
            onLaunch = { app -> SpeedApp.instance.appRepository.launch(app, this) },
            onRename = { newName ->
                var updated: HomeItem? = null
                pages.forEach { grid ->
                    grid.updateFolder(folder.key) { f -> 
                        val u = f.copy(name = newName)
                        if (updated == null) updated = u
                        u
                    }
                }
                // v132: riapri cartella per evitare icone glitchate
                updated?.let { FolderSheet.reopen(it) }
            },
            onRemoveFromFolder = { app ->
                var updated: HomeItem? = null
                var folderRemoved = false
                pages.forEach { grid ->
                    grid.updateFolder(folder.key) { f ->
                        val newApps = f.folderApps - app.key
                        when {
                            newApps.isEmpty() -> { folderRemoved = true; null }
                            newApps.size == 1 -> {
                                folderRemoved = true
                                HomeItem(
                                    key = newApps[0], page = f.page,
                                    cellX = f.cellX, cellY = f.cellY,
                                    type = HomeItem.TYPE_APP
                                )
                            }
                            else -> {
                                val u = f.copy(folderApps = newApps)
                                if (updated == null) updated = u
                                u
                            }
                        }
                    }
                }
                val appInfo = SpeedApp.instance.appRepository.apps.value?.find { it.key == app.key }
                if (appInfo != null && pages.none { it.isPinned(appInfo) }) {
                    pinApp(appInfo)
                }
                // v132: riapri cartella per refreshare icone (no più glitch dopo rimozione)
                if (!folderRemoved) {
                    updated?.let { FolderSheet.reopen(it) }
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
        applyWidgetConfig()
        // v251: search bar visibile indipendentemente da drawer (con tap intelligente)
        binding.searchBar.visibility = if (settings.showSearchBar.value != false) View.VISIBLE else View.GONE
        updateSearchBarText()
        applySearchBarStyle()
        applyAnimationStyle()
    }


    /** v240: container widget multi-widget. Altezza fissa basata su grid widget. */
    fun applyWidgetConfig() {
        val ws = binding.widgetSlot
        val parent = ws.parent as? android.widget.LinearLayout ?: run {
            ws.post { applyWidgetConfig() }
            return
        }
        val density = resources.displayMetrics.density
        // v243: se setting "spazio widget" disattivato, nascondi e basta
        if (settings.showWidgetSlot.value != true) {
            ws.visibility = android.view.View.GONE
            return
        }
        // In landscape nascondo (schermo basso)
        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            ws.visibility = android.view.View.GONE
            return
        }
        ws.visibility = android.view.View.VISIBLE
        // Altezza fissa: 4 righe × 60dp = 240dp (= 4 celle widget verticali)
        val heightPx = (240 * density).toInt()
        val existing = ws.layoutParams
        val lp = if (existing is android.widget.LinearLayout.LayoutParams) existing
                 else android.widget.LinearLayout.LayoutParams(
                     android.widget.LinearLayout.LayoutParams.MATCH_PARENT, heightPx
                 )
        lp.height = heightPx
        lp.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        ws.layoutParams = lp
    }

    fun reapplySettings() {
        refreshRecommended()
        applySettings()
        applyWidgetConfig()  // v206: deve girare anche al rotation change
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 4
        for (page in pages) {
            // v226: rotate non deve sovrascrivere lo store - ricarico SEMPRE da store dopo applyGridSize
            page.applyGridSize(cols, rows, persistChanges = false)
            page.setLayout(layoutStore.loadPage(page.pageIndex))
            // refresh icone per nuova forma/colore dot
            SpeedApp.instance.appRepository.apps.value?.let { page.refresh(it) }
        }
    }

    /**
     * v79: pulisce eventuali residui visivi dopo return dal multitasking.
     * Quando l'utente chiude tutte le app, Android ripristina il launcher snapshot
     * salvato — può lasciare overlay/alpha non resettati. Questo metodo forza tutto
     * allo stato "home pulita" in modo immediato.
     */
    fun cleanupGhostState() {
        // 1. Reset fade overlay (può rimanere visibile se swipe up era stato interrotto)
        fadeOverlay.animate().cancel()
        fadeOverlay.alpha = 0f
        // 2. Reset eventuali transformazioni residue su pages e search bar
        binding.searchBar.alpha = 1f
        binding.searchBar.translationY = 0f
        binding.searchBar.scaleX = 1f
        binding.searchBar.scaleY = 1f
        // 3. Force redraw pulito dell'intero launcher
        invalidate()
        post { invalidate() }
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // v267: intercetto edge-swipe RSS PRIMA che i child (PagedHomeContainer) intercettino
        if (settings.rssPanelEnabled.value == true && ev.action == MotionEvent.ACTION_MOVE && edgeSwipeStarted && !edgeSwipeFired) {
            val dx = ev.x - edgeSwipeStartX
            val dy = kotlin.math.abs(ev.y - edgeSwipeStartY)
            val adx = kotlin.math.abs(dx)
            // Intercetto se chiaramente orizzontale (per preempt PagedHomeContainer)
            // Apertura: swipe destra sulla pagina 0 / Chiusura: swipe sinistra con overlay aperto
            val canOpen = !isRssOverlayOpen && dx > 0 && binding.pagedHome.currentPage == 0
            val canClose = isRssOverlayOpen && dx < 0 && edgeSwipeStartX > width - edgeSize
            if ((canOpen || canClose) && adx > 8f && adx > dy * 1.2f) {
                return true
            }
        }
        // v233: se widget edit mode attivo, NON intercettare swipe → lascia che overlay gestisca tutto
        try {
            if (binding.widgetSlot.isInWidgetEditMode()) return false
        } catch (_: Throwable) {}
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
                swipeFireVibrated = false  // v77: reset flag dedup
                gestureDetector.onTouchEvent(ev)
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = android.view.VelocityTracker.obtain()
                swipeVelocityTracker?.addMovement(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = abs(ev.x - trackStartX)
                val dy = ev.y - trackStartY
                gestureDetector.onTouchEvent(ev)
                swipeVelocityTracker?.addMovement(ev)

                // v26: durante lo swipe up, aggiorna alpha overlay proporzionale
                if (dy < 0 && abs(dy) > dx * 1.0f) {
                    val maxFadeDistance = height * 0.45f
                    val progress = (abs(dy) / maxFadeDistance).coerceIn(0f, 1f)
                    fadeOverlay.alpha = progress * 0.55f
                }

                // v46: check velocity per swipe fulmineo (anche con poca distanza)
                swipeVelocityTracker?.computeCurrentVelocity(1000)
                val vy = swipeVelocityTracker?.yVelocity ?: 0f

                val isFastSwipeUp = vy < -swipeFastVelocity && abs(dy) > dx * 1.0f && dy < 0
                val isSlowSwipeUp = dy < -swipeThreshold && abs(dy) > dx * 1.0f

                if (isFastSwipeUp || isSlowSwipeUp) {
                    tracking = false
                    // v254: su RSS leading page, swipe up NON apre drawer (deve scrollare feed)
                    if (isRssOverlayOpen) {
                        fadeOverlay.animate().alpha(0f).setDuration(120).start()
                        return false
                    }
                    if (settings.drawerEnabled.value != false) {
                        if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                        onSwipeUp?.invoke()
                        fadeOverlay.animate().alpha(0f).setDuration(180).start()
                        return true
                    }
                }
                if (settings.swipeDownNotifications.value == true &&
                    !isRssOverlayOpen &&
                    dy > swipeThreshold && abs(dy) > dx * 1.0f) {
                    tracking = false
                    if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                    StatusBarHelper.expandNotifications(context)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                swipeFireVibrated = false  // v177: reset flag così il prossimo gesto può sparare
                gestureDetector.onTouchEvent(ev)
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
                if (fadeOverlay.alpha > 0f) {
                    fadeOverlay.animate().alpha(0f).setDuration(180).start()
                }
            }
        }
        return false
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // v267: edge-swipe RSS handling anche in onTouchEvent (dopo che onIntercept ha intercettato)
        if (settings.rssPanelEnabled.value == true && edgeSwipeStarted && !edgeSwipeFired) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - edgeSwipeStartX
                    val dy = kotlin.math.abs(event.y - edgeSwipeStartY)
                    val adx = kotlin.math.abs(dx)
                    if (!isRssOverlayOpen && dx > 30f && adx > dy * 1.2f 
                        && binding.pagedHome.currentPage == 0) {
                        edgeSwipeFired = true
                        performHapticFeedbackLight()
                        openRssOverlay()
                        return true
                    }
                    if (isRssOverlayOpen && dx < -60f && adx > dy * 1.5f 
                        && edgeSwipeStartX > width - edgeSize) {
                        edgeSwipeFired = true
                        performHapticFeedbackLight()
                        closeRssOverlay()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    edgeSwipeStarted = false
                }
            }
        }
        return gestureDetector.onTouchEvent(event)
    }

    
    /** v259: parallax sui pages e widgetSlot durante lo scroll orizzontale */
    
    
    
    // v261: RSS overlay - sliding panel da sinistra
    private val rssOverlay: FrameLayout by lazy { findViewById(org.cheipstudio.speedlauncher.R.id.rssOverlay) }
    
    private var rssPanelView: RssPanelView? = null
    var isRssOverlayOpen: Boolean = false
        private set
    
    private fun applyRssOverlayEnabled(enabled: Boolean) {
        if (enabled) {
            if (rssPanelView == null) {
                val v = RssPanelView(context)
                rssPanelView = v
                rssOverlay.removeAllViews()
                rssOverlay.addView(v, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                // v267: callback chiusura overlay via swipe-left interno
                v.onSwipeLeftToClose = { closeRssOverlay() }
                v.reload()
            }
        } else {
            // Setting OFF: chiudi overlay se aperto e rimuovi
            if (isRssOverlayOpen) closeRssOverlay()
            rssPanelView = null
            rssOverlay.removeAllViews()
            rssOverlay.visibility = View.GONE
        }
        updatePageIndicator()
    }
    
    /** v261: apre il pannello RSS con animazione slide da sinistra */
    fun openRssOverlay() {
        if (isRssOverlayOpen) return
        if (rssPanelView == null) return
        isRssOverlayOpen = true
        rssOverlay.visibility = View.VISIBLE
        rssOverlay.translationX = -width.toFloat()
        rssOverlay.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
        updatePageIndicator()
    }
    
    /** v261: chiude il pannello RSS con animazione slide a sinistra */
    fun closeRssOverlay() {
        if (!isRssOverlayOpen) return
        isRssOverlayOpen = false
        rssOverlay.animate()
            .translationX(-width.toFloat())
            .setDuration(280L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .withEndAction { rssOverlay.visibility = View.GONE }
            .start()
        updatePageIndicator()
    }
    
    /** v276: animazione swipe schermata Pixel-style con easing accentuato */
    private fun applyPageParallax(fraction: Float) {
        val pageW = binding.pagedHome.width
        if (pageW <= 0) return
        val totalPages = binding.pagedHome.pageCount.coerceAtLeast(1)
        val scrollX = fraction * (totalPages - 1) * pageW
        for (i in 0 until binding.pagedHome.pageCount) {
            val pageView = binding.pagedHome.getPageAt(i) ?: continue
            val natural = i * pageW
            val delta = natural - scrollX
            val rawDist = (kotlin.math.abs(delta) / pageW).coerceIn(0f, 1f)
            // Easing: accentua l'effetto vicino al centro (sqrt = cresce più rapidamente)
            val distNorm = kotlin.math.sqrt(rawDist)
            // Parallax 25% - leggero offset per profondità
            pageView.translationX = delta * 0.25f
            // Scale: 1 → 0.88 (più aggressivo per effetto visibile)
            val scale = 1f - 0.12f * distNorm
            pageView.scaleX = scale
            pageView.scaleY = scale
            // Alpha: 1 → 0.4 (le pagine laterali sbiadiscono significativamente)
            pageView.alpha = 1f - 0.6f * distNorm
            // Pivot ai bordi opposti per dare sensazione di rotazione/profondità
            pageView.pivotX = if (delta < 0) pageW.toFloat() else 0f
            pageView.pivotY = pageView.height / 2f
        }
    }
    
    private fun isTouchOnMountedWidget(ev: MotionEvent): Boolean {
        return try {
            val myLoc = IntArray(2); getLocationOnScreen(myLoc)
            // 1) widget montato
            val ws = binding.widgetSlot
            val wsLoc = IntArray(2); ws.getLocationOnScreen(wsLoc)
            val wsLeft = wsLoc[0] - myLoc[0]
            val wsTop = wsLoc[1] - myLoc[1]
            if (ev.x >= wsLeft && ev.x <= wsLeft + ws.width &&
                ev.y >= wsTop && ev.y <= wsTop + ws.height) {
                if (ws.isWidgetAt(ev.x - wsLeft, ev.y - wsTop)) return true
            }
            // 2) icona app o cartella nella grid
            if (isTouchOnIconOrFolder(ev)) return true
            // 3) v278: dock (recommendedRow top o bottom) o searchBar
            if (isTouchInView(ev, binding.recommendedRow, myLoc)) return true
            if (isTouchInView(ev, binding.recommendedRowBottom, myLoc)) return true
            if (isTouchInView(ev, binding.searchBar, myLoc)) return true
            false
        } catch (_: Throwable) { false }
    }
    
    private fun isTouchInView(ev: MotionEvent, v: android.view.View?, myLoc: IntArray): Boolean {
        if (v == null || v.visibility != View.VISIBLE) return false
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val left = loc[0] - myLoc[0]
        val top = loc[1] - myLoc[1]
        return ev.x >= left && ev.x <= left + v.width &&
               ev.y >= top && ev.y <= top + v.height
    }
    
    /** v276: ritorna true se il touch è sopra un IconCellView o FolderCellView */
    private fun isTouchOnIconOrFolder(ev: MotionEvent): Boolean {
        return try {
            val ph = binding.pagedHome
            val phLoc = IntArray(2); ph.getLocationOnScreen(phLoc)
            val myLoc = IntArray(2); getLocationOnScreen(myLoc)
            val phLeft = phLoc[0] - myLoc[0]
            val phTop = phLoc[1] - myLoc[1]
            if (ev.x < phLeft || ev.x > phLeft + ph.width ||
                ev.y < phTop || ev.y > phTop + ph.height) return false
            // Itero le pagine, prendo la pagina corrente
            val pageView = ph.getPageAt(ph.currentPage) as? android.view.ViewGroup ?: return false
            val pageLoc = IntArray(2); pageView.getLocationOnScreen(pageLoc)
            val pageLeft = pageLoc[0] - myLoc[0]
            val pageTop = pageLoc[1] - myLoc[1]
            for (i in 0 until pageView.childCount) {
                val child = pageView.getChildAt(i) ?: continue
                val cLeft = pageLeft + child.left
                val cTop = pageTop + child.top
                val cRight = cLeft + child.width
                val cBottom = cTop + child.height
                if (ev.x >= cLeft && ev.x <= cRight && ev.y >= cTop && ev.y <= cBottom) {
                    return true
                }
            }
            false
        } catch (_: Throwable) { false }
    }
    
    fun snapToFirstHomePage() {
        if (isRssOverlayOpen) closeRssOverlay()
        binding.pagedHome.snapToPage(0, animate = false)
    }
    
    // v265: edge-swipe detector per aprire RSS dal bordo sinistro
    private val edgeSize = (40 * resources.displayMetrics.density).toInt()
    
    // v279: long press home detector custom (più tollerante del GestureDetector standard)
    private val homeLongPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val homeLongPressRunnable = Runnable {
        if (isRssOverlayOpen) return@Runnable
        if (edgeSwipeFired) return@Runnable
        if (homeGestureCancelled) return@Runnable
        HapticHelper.longPress(this@HomeView)
        onHomeLongPress?.invoke()
        homeGestureCancelled = true  // evita doppio trigger
    }
    private var edgeSwipeStarted = false
    private var edgeSwipeStartX = 0f
    private var edgeSwipeStartY = 0f
    private var edgeSwipeFired = false
    
    /** v270: escludo edge sinistro dalla back gesture di sistema (Android 10+) */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGestureExclusion()
    }
    
    private fun updateGestureExclusion() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val rect = android.graphics.Rect(0, 0, edgeSize, height)
                systemGestureExclusionRects = listOf(rect)
            }
        } catch (_: Throwable) {}
    }
    
    private var homeGestureStartX = 0f
    private var homeGestureStartY = 0f
    private var homeGestureCancelled = false
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // v279: long press home detector custom (resiste a micro-movimenti del PagedHomeContainer)
        try {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    homeGestureStartX = ev.x
                    homeGestureStartY = ev.y
                    homeGestureCancelled = isTouchOnMountedWidget(ev)
                    homeLongPressHandler.removeCallbacks(homeLongPressRunnable)
                    if (!homeGestureCancelled) {
                        // v280: delay 600ms - più stabile (cancel ha tempo di scattare su swipe lenti)
                        homeLongPressHandler.postDelayed(homeLongPressRunnable, 600L)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!homeGestureCancelled) {
                        val dxRaw = ev.x - homeGestureStartX
                        val dyRaw = ev.y - homeGestureStartY
                        val dx = kotlin.math.abs(dxRaw)
                        val dy = kotlin.math.abs(dyRaw)
                        val density = resources.displayMetrics.density
                        // v280: cancel AGGRESSIVO su movimento verticale (swipe up/down) - 8dp basta
                        // Per orizzontale resta tollerante (25dp) per coprire micro-scroll PagedHomeContainer
                        val verticalCancel = dy > 8 * density && dy > dx * 1.5f
                        val anyCancel = dx > 25 * density || dy > 25 * density
                        if (verticalCancel || anyCancel) {
                            homeGestureCancelled = true
                            homeLongPressHandler.removeCallbacks(homeLongPressRunnable)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    homeLongPressHandler.removeCallbacks(homeLongPressRunnable)
                }
            }
        } catch (_: Throwable) {}
        if (settings.rssPanelEnabled.value == true) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    edgeSwipeStarted = true  // v272: sempre tracking, no edge restriction
                    edgeSwipeFired = false
                    edgeSwipeStartX = ev.x
                    edgeSwipeStartY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (edgeSwipeStarted && !edgeSwipeFired) {
                        val dx = ev.x - edgeSwipeStartX
                        val dy = kotlin.math.abs(ev.y - edgeSwipeStartY)
                        val adx = kotlin.math.abs(dx)
                        // APERTURA: swipe destra ovunque sulla pagina 0 quando overlay chiuso
                        if (!isRssOverlayOpen && dx > 30f && adx > dy * 1.2f
                            && binding.pagedHome.currentPage == 0) {
                            edgeSwipeFired = true
                            // Cancello eventuali long-press pendenti
                            val cancel = MotionEvent.obtain(ev)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            try { super.dispatchTouchEvent(cancel) } catch (_: Throwable) {}
                            cancel.recycle()
                            performHapticFeedbackLight()
                            openRssOverlay()
                            return true
                        }
                        // v274: CHIUSURA solo se DOWN è partito dal bordo destro (NON disturba chip filtri al centro)
                        if (isRssOverlayOpen && dx < -60f && adx > dy * 1.5f 
                            && edgeSwipeStartX > width - edgeSize) {
                            edgeSwipeFired = true
                            performHapticFeedbackLight()
                            closeRssOverlay()
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    edgeSwipeStarted = false
                }
            }
            if (edgeSwipeFired) return true
        }
        return super.dispatchTouchEvent(ev)
    }
    
    fun attachWidgetHost(host: WidgetHostController) { 
        binding.widgetSlot.setHostController(host)
        binding.widgetSlot.onEmptyLongPress = { onHomeLongPress?.invoke() }
        // v271: swipe orizzontale sul widget → cambio pagina come se fosse sulla grid
        binding.widgetSlot.onHorizontalSwipe = { ev ->
            try {
                // Trasformo coordinate da widgetSlot-local a pagedHome-local
                val ws = binding.widgetSlot
                val ph = binding.pagedHome
                val wsLoc = IntArray(2); ws.getLocationOnScreen(wsLoc)
                val phLoc = IntArray(2); ph.getLocationOnScreen(phLoc)
                val offsetX = (wsLoc[0] - phLoc[0]).toFloat()
                val offsetY = (wsLoc[1] - phLoc[1]).toFloat()
                val translated = MotionEvent.obtain(ev)
                translated.setLocation(ev.x + offsetX, ev.y + offsetY)
                ph.dispatchTouchEvent(translated)
                translated.recycle()
            } catch (_: Throwable) {}
        }
    }
    
    // v228: apre il widget picker per la slot corrente (chiamato da HomeMenuSheet "Aggiungi widget")
    fun openWidgetPickerForCurrentSlot() {
        // Forza widgetSlot visible se nascosto
        if (binding.widgetSlot.visibility != View.VISIBLE) {
            settings.setShowWidgetSlot(true)
            binding.widgetSlot.visibility = View.VISIBLE
        }
        binding.widgetSlot.openPicker()
    }
    fun refreshApps(apps: List<AppInfo>) {
        for (page in pages) page.refresh(apps)
        maybeCreateNextPage()
        refreshRecommended()
    }

    /**
     * v88: ricarica tutte le pagine dal layoutStore.
     * Usato dopo addSingleApp (auto-add nuove app installate) per
     * far apparire subito la nuova app senza riavvio.
     */
    fun reloadAllPagesFromStore() {
        val apps = SpeedApp.instance.appRepository.apps.value ?: return
        // Trova max page nel layout
        val items = layoutStore.load()
        val maxPage = items.maxOfOrNull { it.page } ?: 0
        // Espandi pages se serve
        ensurePageExists(maxPage)
        // v228: ricarica pinnedItems dal disco (era il bug auto-add nuove app)
        for (page in pages) {
            page.setLayout(layoutStore.loadPage(page.pageIndex))
            page.refresh(apps)
        }
        updatePageIndicator()
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
        // v204: in landscape forza posizione BOTTOM - schermo basso, rec top + dock bottom 
        // ruberebbero tutto lo spazio alla grid
        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val effectivePos = if (isLandscape) {
            org.cheipstudio.speedlauncher.data.SettingsRepository.REC_POS_BOTTOM
        } else {
            pos
        }
        if (effectivePos == org.cheipstudio.speedlauncher.data.SettingsRepository.REC_POS_BOTTOM) {
            binding.recommendedRow.visibility = android.view.View.GONE
            binding.recommendedRowBottom.visibility = android.view.View.VISIBLE
            binding.recommendedRowBottom.refresh("home")
        } else {
            binding.recommendedRow.visibility = android.view.View.VISIBLE
            binding.recommendedRowBottom.visibility = android.view.View.GONE
            binding.recommendedRow.refresh("home")
        }
    }
    fun refreshDots() {
        for (page in pages) page.invalidate()
        // v67: refresh anche dot del dock raccomandate
        try { binding.recommendedRow.refreshNotificationBadges() } catch (_: Throwable) {}
        try { binding.recommendedRowBottom.refreshNotificationBadges() } catch (_: Throwable) {}
    }

    /** v63+v132: applica visibilità della barra di ricerca.
     *  Legge direttamente SharedPreferences per evitare race con LiveData.value. */
    fun applySearchBarVisibility() {
        val prefs = context.getSharedPreferences("speed_settings", android.content.Context.MODE_PRIVATE)
        val drawerOn = prefs.getBoolean("drawer_enabled", true)
        val showBar = prefs.getBoolean("show_searchbar", true)
        val show = drawerOn && showBar
        binding.searchBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** v61: applica toggle pulitore memoria — aggiunge/rimuove il button da tutte le pagine */
    fun applyMemoryCleanerToggle(enabled: Boolean) {
        if (enabled) {
            val alreadyPresent = pages.any { p ->
                layoutStore.loadPage(p.pageIndex).any {
                    it.type == HomeItem.TYPE_TOOL && it.key == HomeItem.TOOL_MEMORY_CLEANER
                }
            }
            if (!alreadyPresent && pages.isNotEmpty()) {
                pages[0].addMemoryCleanerIfMissing()
            }
        } else {
            for (p in pages) p.removeMemoryCleaner()
        }
    }
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

    /** v27: chiamato da MainActivity quando si preme home dalla home */
    fun snapToFirstPage() {
        // v261: chiudi overlay RSS se aperto + snap a home page 0
        if (isRssOverlayOpen) closeRssOverlay()
        binding.pagedHome.snapToPage(0, animate = true)
        // v226: playWelcomeAnim disabilitato — MainActivity onResume gestisce tutto in modo orchestrato
        // playWelcomeAnim()
    }

    /** v48: applica il tema alla search bar (transparent/light/dark/system) */
    fun applySearchTheme() {
        val theme = SpeedApp.instance.settingsRepository.searchTheme.value ?: "system"
        val card = binding.searchBar as MaterialCardView
        when (theme) {
            "transparent" -> {
                card.setCardBackgroundColor(android.graphics.Color.argb(80, 0, 0, 0))
                card.cardElevation = 0f
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                card.strokeColor = android.graphics.Color.argb(60, 255, 255, 255)
            }
            "light" -> {
                card.setCardBackgroundColor(android.graphics.Color.argb(230, 245, 245, 245))
                card.cardElevation = 2 * resources.displayMetrics.density
                card.strokeWidth = 0
            }
            "dark" -> {
                card.setCardBackgroundColor(android.graphics.Color.argb(230, 27, 27, 31))
                card.cardElevation = 2 * resources.displayMetrics.density
                card.strokeWidth = 0
            }
            else -> {
                // System: usa colorSurfaceContainer
                card.setCardBackgroundColor(resolveAttrColor(com.google.android.material.R.attr.colorSurfaceContainer))
                card.cardElevation = 2 * resources.displayMetrics.density
                card.strokeWidth = 0
            }
        }
    }

    /** v48: applica tema alla dock raccomandate (forwardiamo) */
    fun applyDockTheme() {
        binding.recommendedRow.refreshTheme()
        binding.recommendedRowBottom.refreshTheme()
    }

    private fun resolveAttrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** v45: anim leggera quando torni alla home da app */
    private fun playWelcomeAnim() {
        // Search bar: rapida riappare con leggero scale + alpha
        binding.searchBar.apply {
            alpha = 0.6f
            scaleX = 0.96f; scaleY = 0.96f
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
        }
        // Icone home: stagger leggerissimo (rimane snappy)
        for (i in 0 until pages.size) {
            val grid = pages[i]
            grid.alpha = 0.85f
            grid.translationY = 12f
            grid.animate()
                .alpha(1f).translationY(0f)
                .setDuration(240)
                .setStartDelay((i * 30L).coerceAtMost(60L))
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    /**
     * v26: haptic leggero, single-tap. Su API 30+ usa GESTURE_START (più sottile),
     * altrimenti CLOCK_TICK (era KEYBOARD_TAP — troppo forte).
     * v77: rimosso FLAG_IGNORE_GLOBAL_SETTING (rispetta le impostazioni utente).
     *      Fallback Vibrator usa amplitudine 60 (su 255) e durata 12ms — molto più sottile.
     */
    private fun performHapticFeedbackLight() {
        if (settings.hapticEnabled.value != true) return
        val viewSucceeded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (!viewSucceeded) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(android.os.VibratorManager::class.java)
                    val vib = vm?.defaultVibrator
                    // v77: ampiezza 60/255 (era DEFAULT_AMPLITUDE = ~150) → vibrazione gentile
                    vib?.vibrate(android.os.VibrationEffect.createOneShot(12L, 60))
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    vib?.vibrate(android.os.VibrationEffect.createOneShot(12L, 60))
                } else {
                    @Suppress("DEPRECATION")
                    val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    @Suppress("DEPRECATION")
                    vib?.vibrate(12L)
                }
            } catch (_: Throwable) {}
        }
    }


    /** v132: menu Rinomina/Elimina al long press di una cartella in home.
     *  Design BottomSheet stile AppActionsSheet per consistenza. */
    private fun showFolderMenu(folder: HomeItem) {
        val ctx = context
        val density = ctx.resources.displayMetrics.density
        
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }
        
        // Drag handle
        val handle = android.view.View(ctx).apply {
            background = ctx.getDrawable(org.cheipstudio.speedlauncher.R.drawable.bg_drag_handle)
            val lp = android.widget.LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(handle)
        
        // Titolo (nome cartella)
        val title = android.widget.TextView(ctx).apply {
            text = folder.name ?: ""
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(title)
        
        // Item: Rinomina
        val renameItem = buildSheetItem(ctx, ctx.getString(org.cheipstudio.speedlauncher.R.string.folder_rename_title), android.R.drawable.ic_menu_edit) {
            sheet.dismiss()
            showFolderRenameDialog(folder)
        }
        container.addView(renameItem)
        
        // Item: Elimina
        val deleteItem = buildSheetItem(ctx, ctx.getString(org.cheipstudio.speedlauncher.R.string.folder_delete_confirm_title), android.R.drawable.ic_menu_delete) {
            sheet.dismiss()
            showFolderDeleteDialog(folder)
        }
        container.addView(deleteItem)
        
        sheet.setContentView(container)
        sheet.show()
    }
    
    private fun buildSheetItem(ctx: Context, label: String, iconRes: Int, onClick: () -> Unit): View {
        val density = ctx.resources.displayMetrics.density
        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            run {
                    val tvSel = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
                    setBackgroundResource(tvSel.resourceId)
                }
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        val icon = android.widget.ImageView(ctx).apply {
            setImageResource(iconRes)
            val s = (24 * density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (16 * density).toInt()
            }
        }
        row.addView(icon)
        val txt = android.widget.TextView(ctx).apply {
            text = label
            textSize = 16f
        }
        row.addView(txt)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun showFolderRenameDialog(folder: HomeItem) {
        val ctx = context
        val density = ctx.resources.displayMetrics.density
        val container = android.widget.FrameLayout(ctx).apply {
            val pad = (24 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            setText(folder.name ?: "")
            setSelection((folder.name ?: "").length)
            setSingleLine(true)
            textSize = 18f
            hint = ctx.getString(org.cheipstudio.speedlauncher.R.string.folder_name_hint)
        }
        val til = com.google.android.material.textfield.TextInputLayout(
            ctx, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            setBoxCornerRadii(20f * density, 20f * density, 20f * density, 20f * density)
            addView(input)
        }
        container.addView(til)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            ctx, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(org.cheipstudio.speedlauncher.R.string.folder_rename_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text?.toString()?.takeIf { it.isNotBlank() } ?: return@setPositiveButton
                pages.forEach { grid ->
                    grid.updateFolder(folder.key) { f -> f.copy(name = newName) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun showFolderDeleteDialog(folder: HomeItem) {
        val ctx = context
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            ctx, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(org.cheipstudio.speedlauncher.R.string.folder_delete_confirm_title)
            .setMessage(org.cheipstudio.speedlauncher.R.string.folder_delete_confirm_msg)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteFolder(folder)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** v132: elimina cartella e ripin le app contenute */
    private fun deleteFolder(folder: HomeItem) {
        val keysToReturn = folder.folderApps.toList()
        // Rimuovo la cartella
        pages.forEach { grid ->
            grid.updateFolder(folder.key) { _ -> null }
        }
        // Ripin le app
        val allApps = SpeedApp.instance.appRepository.apps.value ?: return
        for (key in keysToReturn) {
            val app = allApps.find { it.key == key } ?: continue
            if (pages.none { it.isPinned(app) }) {
                pinApp(app)
            }
        }
    }
}
