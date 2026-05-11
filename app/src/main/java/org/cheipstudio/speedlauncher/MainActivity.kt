package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.databinding.ActivityMainBinding
import org.cheipstudio.speedlauncher.ui.AppActionsSheet
import org.cheipstudio.speedlauncher.ui.AppDrawerSheet
import org.cheipstudio.speedlauncher.ui.HomeMenuSheet
import org.cheipstudio.speedlauncher.ui.TutorialOverlay
import org.cheipstudio.speedlauncher.widgets.WidgetHostController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var widgetHostController: WidgetHostController
    private var drawerSheet: AppDrawerSheet? = null
    private var homeMenuSheet: HomeMenuSheet? = null
    private var appActionsSheet: AppActionsSheet? = null
    private var tutorialOverlay: TutorialOverlay? = null



    /** v225: moltiplicatore durata animazioni in base a settings.animationStyle */
    private fun animMul(): Float {
        return when (SpeedApp.instance.settingsRepository.animationStyle.value) {
            org.cheipstudio.speedlauncher.data.SettingsRepository.ANIM_NONE -> 0f
            org.cheipstudio.speedlauncher.data.SettingsRepository.ANIM_FAST -> 0.55f
            else -> 1.0f
        }
    }
    private fun shouldRunAnim(): Boolean = animMul() > 0f
    
    /** v277: anima un child del homeView entrando con translateY offset + fade */
    private fun animateChildEntry(parent: android.view.View, viewId: Int, offsetY: Float, 
                                  startDelay: Long, duration: Long, 
                                  interp: android.view.animation.Interpolator) {
        try {
            val v = parent.findViewById<android.view.View>(viewId) ?: return
            v.animate().cancel()
            v.translationY = offsetY
            v.alpha = 0f
            v.animate()
                .translationY(0f).alpha(1f)
                .setStartDelay(startDelay)
                .setDuration(duration)
                .setInterpolator(interp)
                .start()
        } catch (_: Throwable) {}
    }

    /** 
     * v288: animazione "drop" stile Pixel - l'icona dell'app appena chiusa ritorna alla sua 
     * posizione nella home grid con scale 4x → 1x + translate dal punto del tap.
     * Ritorna true se è stata avviata l'animazione (caller può skippare la parte standard di entry).
     */
    private fun tryAnimateAppIconDrop(durMain: Long): Boolean {
        try {
            // v295: preferisci UsageStatsManager se disponibile (più accurato in multitasking)
            val fgPkg = getLastForegroundPackage()
            val ourLastPkg = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchedPackage
            val ts = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchTimestamp
            // Se UsageStatsManager ci dice un pkg diverso da quello che abbiamo registrato, 
            // l'utente è passato a un'altra app via multitasking → usa quello reale
            val pkg = when {
                fgPkg != null && fgPkg != ourLastPkg -> {
                    // Verifico se la app foreground è anche nella home grid (altrimenti niente anim)
                    if (findHomeIconForPackage(fgPkg) != null) fgPkg else return false
                }
                ourLastPkg != null -> ourLastPkg
                else -> return false
            }
            // Solo se launch < 5 min fa (evita drop per app aperte molto tempo fa)
            // Senza UsageStats permission, accetto solo se < 2 min dal launch (riduce falsi positivi)
            if (fgPkg == null && System.currentTimeMillis() - ts > 2 * 60_000L) return false
            // Trovo l'icona corrispondente nella home
            val targetIcon = findHomeIconForPackage(pkg) ?: return false
            val originX = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchOriginX
            val originY = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchOriginY
            if (originX <= 0f || originY <= 0f) return false
            
            // Coordinate target icon sullo schermo
            val targetLoc = IntArray(2)
            targetIcon.getLocationOnScreen(targetLoc)
            val targetX = targetLoc[0] + targetIcon.width / 2f
            val targetY = targetLoc[1] + targetIcon.height / 2f
            
            // Offset da origin a target
            val deltaX = originX - targetX
            val deltaY = originY - targetY
            
            targetIcon.animate().cancel()
            targetIcon.translationX = deltaX
            targetIcon.translationY = deltaY
            targetIcon.scaleX = 3.5f
            targetIcon.scaleY = 3.5f
            targetIcon.alpha = 0f
            
            // M3 emphasized decelerate
            val interp = androidx.core.view.animation.PathInterpolatorCompat.create(
                0.05f, 0.7f, 0.1f, 1.0f
            )
            
            targetIcon.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setStartDelay(30L)
                .setDuration(durMain + 80)
                .setInterpolator(interp)
                .start()
            
            // Reset al termine
            targetIcon.postDelayed({
                try {
                    targetIcon.translationX = 0f
                    targetIcon.translationY = 0f
                    targetIcon.scaleX = 1f
                    targetIcon.scaleY = 1f
                    targetIcon.alpha = 1f
                } catch (_: Throwable) {}
            }, durMain + 120L)
            
            // Reset una tantum: dopo aver usato, azzero lastLaunched per non rifare l'anim al prossimo onResume
            org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchedPackage = null
            
            return true
        } catch (_: Throwable) { return false }
    }
    
    /** 
     * v292: Cerca l'icona corrispondente al package - prima nella home grid, poi in dock, 
     * poi nelle folder (in quel caso ritorna la folder).
     */
    private fun findHomeIconForPackage(pkg: String): android.view.View? {
        try {
            // 1) Home grid pagine
            val pagedHome = binding.homeView.findViewById<org.cheipstudio.speedlauncher.ui.PagedHomeContainer>(R.id.pagedHome)
            if (pagedHome != null) {
                for (i in 0 until pagedHome.pageCount) {
                    val page = pagedHome.getPageAt(i) as? android.view.ViewGroup ?: continue
                    for (j in 0 until page.childCount) {
                        val child = page.getChildAt(j)
                        if (child is org.cheipstudio.speedlauncher.ui.IconCellView && child.packageName == pkg) {
                            return child
                        }
                        // v292: cartelle - se l'app è dentro una folder, ritorno la folder
                        if (child is org.cheipstudio.speedlauncher.ui.FolderCellView) {
                            try {
                                if (folderContainsPackage(child, pkg)) return child
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
            // 2) Dock top (recommendedRow)
            val dockTop = binding.homeView.findViewById<android.view.ViewGroup>(R.id.recommendedRow)
            findIconInGroup(dockTop, pkg)?.let { return it }
            // 3) Dock bottom (recommendedRowBottom)
            val dockBot = binding.homeView.findViewById<android.view.ViewGroup>(R.id.recommendedRowBottom)
            findIconInGroup(dockBot, pkg)?.let { return it }
        } catch (_: Throwable) {}
        return null
    }
    
    /** v293: Cerca ricorsivamente un'icona col packageName dentro un ViewGroup (per dock) */
    private fun findIconInGroup(group: android.view.ViewGroup?, pkg: String): android.view.View? {
        if (group == null || group.visibility != android.view.View.VISIBLE) return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            // IconCellView match diretto
            if (child is org.cheipstudio.speedlauncher.ui.IconCellView && child.packageName == pkg) {
                return child
            }
            // v293: dock cell match per tag "dockcell:<pkg>"
            val tag = child.tag as? String
            if (tag != null && tag == "dockcell:$pkg") {
                return child
            }
            if (child is android.view.ViewGroup) {
                val found = findIconInGroup(child, pkg)
                if (found != null) return found
            }
        }
        return null
    }
    
    /** Verifica se una folder contiene una app con questo packageName */
    private fun folderContainsPackage(folder: org.cheipstudio.speedlauncher.ui.FolderCellView, pkg: String): Boolean {
        return try {
            val item = folder.folder ?: return false
            // folderApps è una List<String> di "package/component"
            item.folderApps.any { entry -> 
                entry.substringBefore("/") == pkg || entry == pkg
            }
        } catch (_: Throwable) { false }
    }
    


    
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v81: disabilita snapshot della Activity per evitare ghost dopo multitasking close
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                setRecentsScreenshotEnabled(false)
            } catch (_: Throwable) {}
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        // v23: richiedi refresh rate massimo (120Hz se disponibile)
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val maxRate = display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 60f
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxRate
            }
        } catch (_: Throwable) {}

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // v30: orientation in base al setting (default = portrait only)
        applyOrientationLock()

        // v38+v41: applica dim e blur del wallpaper
        applyWallpaperDim()
        applyWallpaperBlur()

        // v48: applica tema search bar + dock raccomandate
        binding.homeView.applySearchTheme()
        binding.homeView.applyDockTheme()
        binding.homeView.applySearchBarVisibility()

        // v38: applica lingua scelta dall'utente al primo onCreate
        val langCode = SpeedApp.instance.settingsRepository.language.value ?: "auto"
        if (langCode != "auto") {
            try {
                LanguageHelper.applyLanguage(langCode)
            } catch (_: Throwable) {}
        }

        widgetHostController = WidgetHostController(this).also { it.start() }
        // v240: migrazione one-time dal vecchio sistema single-widget
        try {
            org.cheipstudio.speedlauncher.data.WidgetStore(this).migrateFromLegacyIfNeeded(this)
        } catch (_: Throwable) {}
        binding.homeView.attachWidgetHost(widgetHostController)

        SpeedApp.instance.notificationCounter.counts.observe(this) { binding.homeView.refreshDots() }
        // v61: osservo toggle pulitore memoria per aggiungere/rimuovere il button razzo dalla home
        SpeedApp.instance.settingsRepository.memoryCleanerEnabled.observe(this) { enabled ->
            binding.homeView.applyMemoryCleanerToggle(enabled == true)
        }
        // v63: osservo toggle "mostra barra ricerca"
        SpeedApp.instance.settingsRepository.showSearchBar.observe(this) {
            binding.homeView.applySearchBarVisibility()
        }
        
        // v138: observers per riconfigurare widget al cambio settings
        SpeedApp.instance.settingsRepository.widgetPosition.observe(this) {
            binding.homeView.applyWidgetConfig()
        }
        SpeedApp.instance.settingsRepository.widgetHeight.observe(this) {
            binding.homeView.applyWidgetConfig()
        }
        SpeedApp.instance.settingsRepository.widgetWidthPercent.observe(this) {
            binding.homeView.applyWidgetConfig()
        }
// v114: osservo toggle "drawer abilitato" per aggiornare barra
        SpeedApp.instance.settingsRepository.drawerEnabled.observe(this) {
            binding.homeView.applySearchBarVisibility()
        }
        SpeedApp.instance.appRepository.apps.observe(this) { binding.homeView.refreshApps(it) }

        binding.homeView.onSwipeUp = { openDrawer() }
        // v140: swipe destra dal bordo sinistro → pannello RSS
        binding.homeView.onSwipeRightFromLeftEdge = {
            try {
                startActivity(android.content.Intent(this, org.cheipstudio.speedlauncher.RssActivity::class.java))
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            } catch (_: Throwable) {}
        }
        binding.homeView.onSearchTap = {
            // v252: search tap intelligente
            val settings = SpeedApp.instance.settingsRepository
            val drawerOn = settings.drawerEnabled.value != false
            val mode = settings.searchMode.value
            val isApps = mode == org.cheipstudio.speedlauncher.data.SettingsRepository.MODE_APPS
            if (!drawerOn && isApps) {
                // Drawer off + modalità "App" → nessun drawer da aprire
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.search_drawer_off_apps_only),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                openDrawerWithSearch()
            }
        }
        binding.homeView.onHomeLongPress = { openHomeMenu() }
        binding.homeView.onAppMenuRequest = { app -> openAppActions(app) }
        // v59: tap pulitore memoria → pulisce + snackbar
        binding.homeView.onMemoryCleanerRequest = {
            try {
                val freedMb = org.cheipstudio.speedlauncher.tools.MemoryCleaner.clean(this)
                val msg = if (freedMb > 0)
                    getString(R.string.memory_cleaned_with_amount, freedMb)
                else
                    getString(R.string.memory_cleaned)
                // v62: snackbar custom con icona Speed Launcher
                val snackbar = com.google.android.material.snackbar.Snackbar.make(
                    binding.root, msg,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                )
                try {
                    val sbView = snackbar.view
                    val tv = sbView.findViewById<android.widget.TextView>(
                        com.google.android.material.R.id.snackbar_text)
                    val icon = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
                    if (icon != null) {
                        val density = resources.displayMetrics.density
                        val sz = (24 * density).toInt()
                        icon.setBounds(0, 0, sz, sz)
                        tv?.setCompoundDrawables(icon, null, null, null)
                        tv?.compoundDrawablePadding = (12 * density).toInt()
                    }
                    // Background con corner Material 3
                    sbView.background = androidx.core.content.ContextCompat.getDrawable(this,
                        R.drawable.bg_snackbar_speed) ?: sbView.background
                } catch (_: Throwable) {}
                snackbar.show()

                // v62: aggiorno il widget Speed Stats per mostrare la nuova RAM libera
                try {
                    org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider.refreshAll(this)
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // v240: edit mode multi-widget gestito dal sheet, niente check qui
                drawerSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
                homeMenuSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
                appActionsSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            }
        })

        if (SpeedApp.instance.settingsRepository.tutorialSeen.value != true) {
            showTutorial()
        }
    }

    private fun applyWallpaperDim() {
        val dim = SpeedApp.instance.settingsRepository.wallpaperDim.value ?: 0
        val alpha = (dim.coerceIn(0, 100)) / 100f
        binding.homeView.setDimOverlayAlpha(alpha)
    }

    /**
     * v41: applica RenderEffect blur al wallpaper.
     * Solo API 31+. Il blur viene applicato al decorView per sfocare TUTTO ciò
     * che sta sotto la finestra dell'app — quindi anche il wallpaper di sistema.
     * NOTA: tecnicamente blurra anche l'app, ma l'effetto pratico su uno sfondo
     * scuro/chiaro è impercettibile. Per blur SOLO del wallpaper servirebbe
     * Window.setBackgroundBlurRadius (API 31+) che è la soluzione corretta.
     */
    /**
     * v58: applica blur SUL WALLPAPER catturando il drawable di sistema e renderizzandolo
     * in un ImageView dietro le icone con setRenderEffect.
     * Stesso approccio delle cartelle, funziona su Android 12+ senza richiedere cross-window blur.
     */
    private fun applyWallpaperBlur() {
        val radius = SpeedApp.instance.settingsRepository.wallpaperBlur.value ?: 0
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        
        try {
            if (radius == 0) {
                // Disattiva blur sul wallpaper
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 0
                    flags = flags and android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
            } else {
                // FLAG_BLUR_BEHIND su windowShowWallpaper sfoca il wallpaper sotto
                val r = radius.coerceIn(1, 150)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = r
                    flags = flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                }
            }
        } catch (_: Throwable) {}
    }

    private fun applyOrientationLock() {
        val allowLandscape = SpeedApp.instance.settingsRepository.landscapeAllowed.value == true
        requestedOrientation = if (allowLandscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun showTutorial() {
        tutorialOverlay = TutorialOverlay(this).also {
            (binding.root as android.widget.FrameLayout).addView(
                it,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // v209: NON ricreo (causava schermo nero), riapplico solo i settings  
        try {
            binding.homeView.reapplySettings()
        } catch (_: Throwable) {}
    }

    private var pauseTimestamp = 0L
    
    override fun onResume() {
        super.onResume()
        // v250: ritorno alla home → skip RSS leading page
        // v292: se torno da un'app aperta dalla home, snap alla pagina di partenza (non a pagina 1)
        try {
            val lastPage = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchPageIndex
            val ts = org.cheipstudio.speedlauncher.data.AppRepository.lastLaunchTimestamp
            val isRecentAppReturn = lastPage >= 0 && (System.currentTimeMillis() - ts) < 5 * 60_000L
            if (isRecentAppReturn) {
                binding.homeView.snapToPage(lastPage)
            } else {
                binding.homeView.snapToFirstHomePage()
            }
        } catch (_: Throwable) {}

        // v218: forza visibilità in landscape (fix schermo nero)
        try {
            val isLand = resources.configuration.orientation == 
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLand) {
                binding.homeView.alpha = 1f
                binding.homeView.scaleX = 1f
                binding.homeView.scaleY = 1f
                binding.homeView.translationY = 0f
                binding.homeView.visibility = android.view.View.VISIBLE
                // Reset anche su tutti i child principali
                for (id in intArrayOf(R.id.widgetSlot, R.id.searchBar, R.id.pageIndicator,
                                      R.id.recommendedRow, R.id.recommendedRowBottom,
                                      R.id.pagedHome)) {
                    try {
                        binding.homeView.findViewById<android.view.View>(id)?.let {
                            it.alpha = 1f
                            it.scaleX = 1f
                            it.scaleY = 1f
                            it.translationY = 0f
                            it.translationX = 0f
                        }
                    } catch (_: Throwable) {}
                }
                binding.homeView.invalidate()
                binding.homeView.requestLayout()
            }
        } catch (e: Throwable) {

        }
        // v215: animazione entrata home SOLO se in pausa per >800ms (esclude tap widget, focus events)
        val pauseDuration = if (pauseTimestamp > 0) System.currentTimeMillis() - pauseTimestamp else 0
        val shouldAnimate = pauseTimestamp > 0 && pauseDuration > 400 && shouldRunAnim()
        pauseTimestamp = 0L
        if (!shouldAnimate) {
            // Garantisco visibilità  
            try {
                binding.homeView.alpha = 1f
                binding.homeView.scaleX = 1f
                binding.homeView.scaleY = 1f
            } catch (_: Throwable) {}
        }
        // v277: animazione ritorno home - elementi entrano da off-screen con fade
        try {
            val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val homeContent = binding.homeView
            homeContent.animate().cancel()
            homeContent.translationX = 0f; homeContent.translationY = 0f
            homeContent.alpha = 1f; homeContent.scaleX = 1f; homeContent.scaleY = 1f
            
            if (!isLandscape && shouldAnimate) {
                val density = resources.displayMetrics.density
                val offsetTop = 40 * density   // 40dp
                val offsetBot = 40 * density
                val durMain = (380 * animMul()).toLong()
                val interp = android.view.animation.DecelerateInterpolator(2.0f)
                
                // Top elements (widget + dock top) - dall'alto
                animateChildEntry(binding.homeView, R.id.widgetSlot, -offsetTop, 0L, durMain, interp)
                animateChildEntry(binding.homeView, R.id.recommendedRow, -offsetTop, 30L, durMain, interp)
                
                // Grid pages - fade + scale leggero
                val pagedHome = binding.homeView.findViewById<android.view.View>(R.id.pagedHome)
                pagedHome?.let { v ->
                    v.animate().cancel()
                    v.alpha = 0f
                    v.scaleX = 0.92f; v.scaleY = 0.92f
                    v.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(60L)
                        .setDuration(durMain)
                        .setInterpolator(interp)
                        .start()
                }
                // v288: drop animation icona app (stile Pixel) - dopo che il pagedHome è visibile
                binding.homeView.postDelayed({
                    tryAnimateAppIconDrop(durMain)
                }, 100L)
                
                // Bottom elements (dock bottom + search) - dal basso
                animateChildEntry(binding.homeView, R.id.pageIndicator, offsetBot, 90L, durMain, interp)
                animateChildEntry(binding.homeView, R.id.recommendedRowBottom, offsetBot, 120L, durMain, interp)
                animateChildEntry(binding.homeView, R.id.searchBar, offsetBot, 150L, durMain, interp)
            }
        } catch (_: Throwable) {}
        widgetHostController.startListening()
        binding.homeView.reapplySettings()
        // v30: ri-applica orientation se è cambiata
        applyOrientationLock()
        // v38+v41: applica cambi dim + blur
        applyWallpaperDim()
        applyWallpaperBlur()
        // v48: re-apply temi (in caso cambiati da settings)
        binding.homeView.applySearchTheme()
        binding.homeView.applyDockTheme()
        // v79: cleanup ghost residui dopo return dal multitasking
        binding.homeView.cleanupGhostState()
        
        // v273: force redraw SOLO se pausa lunga (multitasking close, > 5s)
        // Per chiusure rapide app, niente refresh visibile → home fluida
        if (pauseDuration > 5000) {
            binding.homeView.post {
                binding.homeView.cleanupGhostState()
                binding.homeView.requestLayout()
                binding.homeView.invalidate()
                binding.root.invalidate()
            }
        }
    }
    
    // v273: focus cleanup solo per ghost state (no invalidate forced)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::widgetHostController.isInitialized) {
            try {
                binding.homeView.cleanupGhostState()
            } catch (_: Throwable) {}
        }
    }

    /** v133: se NON sono il default launcher e l'utente fa gesto home, mi chiudo
     *  (comportamento app normale invece che launcher). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            val isDefaultLauncher = resolveInfo?.activityInfo?.packageName == packageName
            if (!isDefaultLauncher) {
                // Non sono il default → mi comporto come app normale, esco dalla recents
                finish()
            }
        } catch (_: Throwable) {}
    }
    
    override fun onPause() {
        super.onPause()

        widgetHostController.stopListening()
        pauseTimestamp = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHome) {
            drawerSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            homeMenuSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            appActionsSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            // v27: pressione tasto home da home → torna a pagina 1 con animazione
            binding.homeView.snapToFirstPage()
        }
    }

    @Volatile private var drawerOpenInFlight = false
    
    private fun openDrawer() {
        // v178: gestione robusta - lock atomico per evitare doppia apertura
        if (drawerOpenInFlight) return
        if (supportFragmentManager.isStateSaved) return  // post-onSaveInstanceState
        
        // Check se già aperto/in apertura
        val existing = drawerSheet
        if (existing != null && existing.isAdded) return
        
        drawerOpenInFlight = true
        cleanupOldDrawer()
        
        // Pulizia + apertura sempre via post (fragment transactions safe)
        binding.homeView.post {
            try {
                cleanupOldDrawer()
                doShowDrawer()
            } finally {
                drawerOpenInFlight = false
            }
        }
    }
    
    private fun doShowDrawer() {
        if (supportFragmentManager.isStateSaved) {
            drawerSheet = null
            return
        }
        try {
            // Doppio check post-cleanup
            if (supportFragmentManager.findFragmentByTag("drawer") != null) {
                cleanupOldDrawer()
            }
            val sheet = AppDrawerSheet()
            sheet.onAppLongPress = { app -> openAppActions(app) }
            sheet.onDismissCallback = { 
                drawerSheet = null
                animateHomeBlur(false)
                // v200: reset scale home al chiudere drawer
                try {
                    binding.homeView.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(320)
                        .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                        .start()
                } catch (_: Throwable) {}
            }
            sheet.show(supportFragmentManager, "drawer")
            drawerSheet = sheet
            animateHomeBlur(true)
            // v200: scale-down sottile della home all'apertura drawer  
            try {
                binding.homeView.animate()
                    .scaleX(0.96f).scaleY(0.96f)
                    .setDuration((280 * animMul()).toLong())
                    .setInterpolator(android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                    .start()
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "doShowDrawer failed", t)
            drawerSheet = null
            // Reset blur in caso era stato applicato
            animateHomeBlur(false)
        }
    }
    
    /** v135: blur animato sulla home (apertura/chiusura drawer) — stesso stile cartelle */
    private fun animateHomeBlur(open: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        // v140: rispetta toggle sfocatura drawer
        if (open && SpeedApp.instance.settingsRepository.blurDrawer.value != true) return
        val home = binding.homeView
        val from = if (open) 0f else 24f
        val to = if (open) 24f else 0f
        val anim = android.animation.ValueAnimator.ofFloat(from, to)
        anim.duration = if (open) 220 else 180
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.addUpdateListener { v ->
            try {
                val r = v.animatedValue as Float
                if (r > 0.5f) {
                    home.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(r, r,
                            android.graphics.Shader.TileMode.CLAMP)
                    )
                } else {
                    home.setRenderEffect(null)
                }
            } catch (_: Throwable) {}
        }
        anim.start()
    }

    private fun openDrawerWithSearch() {
        if (drawerSheet?.isAdded == true || drawerSheet?.isVisible == true) return
        cleanupOldDrawer()
        drawerSheet = AppDrawerSheet.newInstance(focusSearch = true).also {
            it.onAppLongPress = { app -> openAppActions(app) }
            try { it.show(supportFragmentManager, "drawer") } catch (_: Throwable) {}
        }
    }

    /** v59: rimuove qualsiasi fragment drawer fantasma */
    private fun cleanupOldDrawer() {
        try {
            supportFragmentManager.executePendingTransactions()
            val existing = supportFragmentManager.findFragmentByTag("drawer")
            if (existing != null) {
                supportFragmentManager.beginTransaction().remove(existing).commitNowAllowingStateLoss()
            }
            // Rimuovo anche eventuali altri DialogFragment "stuck"
            for (f in supportFragmentManager.fragments.toList()) {
                if (f is AppDrawerSheet && f != drawerSheet) {
                    supportFragmentManager.beginTransaction().remove(f).commitNowAllowingStateLoss()
                }
            }
        } catch (_: Throwable) {}
    }

    private fun openHomeMenu() {
        if (homeMenuSheet?.isAdded == true) return
        homeMenuSheet = HomeMenuSheet().also {
            // v283: check se la pagina corrente ha già un widget
            it.currentPageHasWidget = try {
                val pagedHome = binding.homeView.findViewById<org.cheipstudio.speedlauncher.ui.PagedHomeContainer>(R.id.pagedHome)
                val pageIdx = pagedHome?.currentPage ?: 0
                org.cheipstudio.speedlauncher.data.WidgetStore(this).loadPage(pageIdx).isNotEmpty()
            } catch (_: Throwable) { false }
            it.onSettings = {
                startActivity(Intent(this, SettingsIndexActivity::class.java))
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            }
            it.onSorted = {
                recreate()
            }
            it.onManagePages = {
                org.cheipstudio.speedlauncher.ui.PageManagerSheet.show(
                    context = this,
                    getPageCount = { binding.homeView.getPageCount() },
                    getPageIconCount = { idx -> binding.homeView.getPageIconCount(idx) },
                    onAddPage = { binding.homeView.addEmptyPage() },
                    onRemovePage = { idx -> binding.homeView.forceRemovePageAt(idx) }
                )
            }
            // v228: aggiungi widget → apre picker direttamente sul widget slot
            it.onAddWidget = {
                try {
                    binding.homeView.openWidgetPickerForCurrentSlot()
                } catch (_: Throwable) {}
            }
            it.show(supportFragmentManager, "homemenu")
        }
    }

    private fun openAppActions(app: AppInfo) {
        if (appActionsSheet?.isAdded == true) return
        appActionsSheet = AppActionsSheet.newInstance(app).also {
            it.isPinned = { a -> binding.homeView.isPinned(a) }
            it.onPinToggle = { a ->
                if (binding.homeView.isPinned(a)) binding.homeView.unpinApp(a)
                else binding.homeView.pinApp(a)
            }
            it.show(supportFragmentManager, "appactions")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetHostController.handleActivityResult(requestCode, resultCode, data)
    }
}
