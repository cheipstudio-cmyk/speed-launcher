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
        SpeedApp.instance.appRepository.apps.observe(this) { binding.homeView.refreshApps(it) }

        binding.homeView.onSwipeUp = { openDrawer() }
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

        // ImageView wallpaper blur: lo creo una sola volta come primo child del binding.root (FrameLayout)
        val rootChild = binding.root as? android.view.ViewGroup ?: return

        var blurView = rootChild.findViewById<android.widget.ImageView>(R.id.wallpaperBlurView)
        if (blurView == null) {
            blurView = android.widget.ImageView(this).apply {
                id = R.id.wallpaperBlurView
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            rootChild.addView(blurView, 0)  // come PRIMO child = sotto tutto
        }

        if (radius == 0) {
            // Niente blur: rimuovo la visualizzazione del wallpaper catturato
            try { blurView.setRenderEffect(null) } catch (_: Throwable) {}
            blurView.visibility = android.view.View.GONE
            return
        }

        try {
            // Catturo il wallpaper di sistema
            val wm = android.app.WallpaperManager.getInstance(this)
            val drawable = try { wm.drawable } catch (_: Throwable) { null }
            if (drawable != null) {
                blurView.setImageDrawable(drawable)
                blurView.visibility = android.view.View.VISIBLE
                val r = radius.coerceIn(1, 100).toFloat()
                blurView.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(r, r,
                        android.graphics.Shader.TileMode.CLAMP)
                )
            } else {
                blurView.visibility = android.view.View.GONE
            }
        } catch (_: Throwable) {
            blurView.visibility = android.view.View.GONE
        }
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

    override fun onResume() {
        super.onResume()
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

    private fun openDrawer() {
        // v59: rimuovo qualsiasi fragment "drawer" precedente per prevenire doppi
        if (drawerSheet?.isAdded == true || drawerSheet?.isVisible == true) return
        cleanupOldDrawer()
        drawerSheet = AppDrawerSheet().also {
            it.onAppLongPress = { app -> openAppActions(app) }
            try { it.show(supportFragmentManager, "drawer") } catch (_: Throwable) {}
        }
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
                startActivity(Intent(this, SettingsActivity::class.java))
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
