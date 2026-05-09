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
            // v120: NON consumo onDown se drawer è disabilitato e nessun gesto verticale è abilitato
            // (evita glitch e refresh inutili della home quando l'utente fa swipe a vuoto)
            val drawerOn = settings.drawerEnabled.value != false
            val swipeDownOn = settings.swipeDownNotifications.value == true
            return drawerOn || swipeDownOn
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            // v77: dedup vibrazione — usa flag swipeFireVibrated, una sola vibrazione per gesto.
            if (vy < -500f && abs(vy) > abs(vx) * 1.0f) {
                // v85: rispetta drawerEnabled
                if (settings.drawerEnabled.value != false) {
                    if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                    onSwipeUp?.invoke()
                    return true
                }
                // v120: drawer disabilitato → ignoro fling up senza consumare
                return false
            }
            if (settings.swipeDownNotifications.value == true &&
                vy > 500f && abs(vy) > abs(vx) * 1.0f) {
                if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                StatusBarHelper.expandNotifications(context)
                return true
            }
            // v140: swipe destra dal bordo sinistro → pannello RSS
            if (settings.rssPanelEnabled.value == true &&
                vx > 800f && abs(vx) > abs(vy) * 1.5f &&
                e1 != null && e1.x < (resources.displayMetrics.density * 50)) {
                if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                onSwipeRightFromLeftEdge?.invoke()
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
        binding.btnHomeMenu.setOnClickListener { onHomeLongPress?.invoke() }

        // v46: long press home robusto via GestureDetector (più affidabile di setOnLongClickListener su scroll view)
        val homeGesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Ignoro se c'è un'icona sotto (le icone consumano onTouch)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onHomeLongPress?.invoke()
            }
        })
        binding.pagedHome.setOnTouchListener { _, ev ->
            homeGesture.onTouchEvent(ev)
            false  // non consumo: PagedHomeContainer continua a gestire scroll
        }

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
                } catch (_: Throwable) {
                    // v85: solo se drawer abilitato apre fallback
                    if (settings.drawerEnabled.value != false) onSearchTap?.invoke()
                }
            }
            else -> {
                // v85: solo se drawer abilitato apre il drawer
                if (settings.drawerEnabled.value != false) onSearchTap?.invoke()
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
        // v113: barra ricerca nascosta se drawer disabilitato (anche se showSearchBar è true)
        // perché senza drawer la barra non ha funzione (apre il drawer che non esiste)
        val drawerOn = settings.drawerEnabled.value != false
        val searchBarVisible = drawerOn && settings.showSearchBar.value != false
        binding.searchBar.visibility = if (searchBarVisible) View.VISIBLE else View.GONE
        updateSearchBarText()
        applySearchBarStyle()
        applyAnimationStyle()
    }


    /** v138: applica posizione/altezza/larghezza widget secondo le settings */
    fun applyWidgetConfig() {
        val ws = binding.widgetSlot
        val parent = ws.parent as? android.widget.LinearLayout ?: return
        val density = resources.displayMetrics.density
        
        // Altezza
        val heightDp = settings.widgetHeight.value ?: 160
        val heightPx = (heightDp * density).toInt()
        
        // Larghezza percentuale
        val widthPct = settings.widgetWidthPercent.value ?: 100
        
        val lp = ws.layoutParams as? android.widget.LinearLayout.LayoutParams
            ?: android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        lp.height = heightPx
        if (widthPct >= 100) {
            lp.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        } else {
            // Larghezza percentuale: calcolo ad ogni layout pass
            lp.width = (parent.width * widthPct / 100).coerceAtLeast(0)
            if (lp.width <= 0) {
                // parent non ancora misurato, uso match_parent come fallback iniziale
                lp.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                ws.post {
                    val lp2 = ws.layoutParams as android.widget.LinearLayout.LayoutParams
                    lp2.width = (parent.width * widthPct / 100).coerceAtLeast(1)
                    ws.layoutParams = lp2
                }
            }
        }
        // Centro orizzontalmente la widget se non full width
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        ws.layoutParams = lp
        
        // Posizione: sposta la widgetSlot all'interno del parent LinearLayout
        // Top: index 0, Middle: index dopo searchBar+recommended, Bottom: prima di searchBar finale
        val pos = settings.widgetPosition.value ?: "top"
        val currentIdx = parent.indexOfChild(ws)
        val targetIdx = when (pos) {
            "bottom" -> {
                // L'ultimo prima delle voci finali. Cerco l'indice della searchBar (in fondo) e metto prima.
                val searchIdx = parent.indexOfChild(binding.searchBar)
                if (searchIdx >= 0) searchIdx else parent.childCount - 1
            }
            "middle" -> {
                // Subito sopra il pageIndicator
                val pgIdx = parent.indexOfChild(binding.pageIndicator)
                if (pgIdx >= 0) pgIdx else 1
            }
            else -> 0  // top
        }
        if (currentIdx != targetIdx && currentIdx >= 0 && targetIdx >= 0 && targetIdx <= parent.childCount) {
            parent.removeView(ws)
            val safeIdx = targetIdx.coerceAtMost(parent.childCount)
            parent.addView(ws, safeIdx)
        }
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
                    // v85: rispetta drawerEnabled
                    if (settings.drawerEnabled.value != false) {
                        if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                        onSwipeUp?.invoke()
                        fadeOverlay.animate().alpha(0f).setDuration(180).start()
                        return true
                    }
                }
                if (settings.swipeDownNotifications.value == true &&
                    dy > swipeThreshold && abs(dy) > dx * 1.0f) {
                    tracking = false
                    if (!swipeFireVibrated) { performHapticFeedbackLight(); swipeFireVibrated = true }
                    StatusBarHelper.expandNotifications(context)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
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
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    fun attachWidgetHost(host: WidgetHostController) { binding.widgetSlot.setHostController(host) }
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
        // Refresh ogni pagina
        for (page in pages) page.refresh(apps)
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
        if (binding.pagedHome.currentPage > 0) {
            binding.pagedHome.snapToPage(0, animate = true)
        }
        // v45: animazione "welcome back" quando torni alla home
        playWelcomeAnim()
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
            setBackgroundResource(android.R.drawable.list_selector_background)
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
