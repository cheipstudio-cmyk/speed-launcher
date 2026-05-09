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
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (_: Throwable) {}
        }
        binding.homeView.onSearchTap = { openDrawerWithSearch() }
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

    override fun onResume() {
        super.onResume()
        // v210: animazione entrata home Material Expressive — fade dolce
        // SAFETY: alpha torna a 1 anche se animazione viene cancellata (fix schermo nero landscape)
        try {
            val homeContent = binding.homeView
            homeContent.alpha = 0f
            homeContent.scaleX = 1.04f
            homeContent.scaleY = 1.04f
            homeContent.animate().cancel()
            homeContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                .withEndAction { homeContent.alpha = 1f; homeContent.scaleX = 1f; homeContent.scaleY = 1f }
                .start()
            // Fallback: se entro 600ms l'anim non finisce, forza i valori
            homeContent.postDelayed({
                if (homeContent.alpha < 1f) {
                    homeContent.alpha = 1f
                    homeContent.scaleX = 1f
                    homeContent.scaleY = 1f
                }
            }, 600)
            
            // Rimbalzo widget - spring effect
            try {
                val v = binding.homeView.findViewById<android.view.View>(R.id.widgetSlot)
                v?.let {
                    it.scaleX = 0.85f
                    it.scaleY = 0.85f
                    it.animate()
                        .scaleX(1f).scaleY(1f)
                        .setStartDelay(60)
                        .setDuration(420)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                        .start()
                }
            } catch (_: Throwable) {}
            
            // Rimbalzo search bar - spring effect (translateY con bounce)
            try {
                val v = binding.homeView.findViewById<android.view.View>(R.id.searchBar)
                v?.let {
                    it.translationY = 60f
                    it.alpha = 0f
                    it.animate()
                        .translationY(0f).alpha(1f)
                        .setStartDelay(100)
                        .setDuration(420)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.8f))
                        .start()
                }
            } catch (_: Throwable) {}
            
            // Page indicator pop
            try {
                val v = binding.homeView.findViewById<android.view.View>(R.id.pageIndicator)
                v?.let {
                    it.scaleX = 0.5f; it.scaleY = 0.5f; it.alpha = 0f
                    it.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setStartDelay(140)
                        .setDuration(360)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                        .start()
                }
            } catch (_: Throwable) {}
            
            // v202: Dock raccomandate (top + bottom) - rimbalzo dal basso
            for (id in intArrayOf(R.id.recommendedRow, R.id.recommendedRowBottom)) {
                try {
                    val v = binding.homeView.findViewById<android.view.View>(id)
                    v?.let {
                        if (it.visibility == android.view.View.VISIBLE) {
                            it.translationY = 50f
                            it.alpha = 0f
                            it.animate()
                                .translationY(0f).alpha(1f)
                                .setStartDelay(180)
                                .setDuration(440)
                                .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                                .start()
                        }
                    }
                } catch (_: Throwable) {}
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
        
        // v122: force redraw immediato per evitare empty state lungo dopo
        // chiusura completa del multitasking. Forza il re-rendering della home.
        binding.homeView.post {
            binding.homeView.cleanupGhostState()
            binding.homeView.requestLayout()
            binding.homeView.invalidate()
            binding.root.invalidate()
        }
    }
    
    // v132: cleanup AGGRESSIVO al ritorno focus (es. dopo multitasking close)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::widgetHostController.isInitialized) {
            try {
                binding.homeView.cleanupGhostState()
                binding.homeView.invalidate()
                binding.root.invalidate()
                // Force frame refresh
                binding.homeView.post {
                    binding.homeView.requestLayout()
                    binding.homeView.invalidate()
                }
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
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
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
                    .setDuration(280)
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
            it.onSettings = {
                startActivity(Intent(this, SettingsIndexActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
