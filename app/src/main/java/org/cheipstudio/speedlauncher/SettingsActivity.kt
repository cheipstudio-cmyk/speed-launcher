package org.cheipstudio.speedlauncher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cheipstudio.speedlauncher.data.SettingsRepository
import org.cheipstudio.speedlauncher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = SpeedApp.instance.settingsRepository

    private val exportLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val content = BackupManager.exportToJson(this)
        val ok = BackupManager.writeToUri(this, uri, content)
        Toast.makeText(this,
            if (ok) R.string.backup_exported_ok else R.string.backup_export_fail,
            Toast.LENGTH_SHORT).show()
    }

    private val importLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val content = BackupManager.readFromUri(this, uri) ?: run {
            Toast.makeText(this, R.string.backup_import_fail, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val res = BackupManager.importFromJson(this, content)
        if (res.isSuccess) {
            Toast.makeText(this, R.string.backup_imported_ok, Toast.LENGTH_LONG).show()
            // v94: aspetto 800ms che il toast sia visto, poi restart per applicare il backup.
            // Senza delay, forceRestartApp() killa il processo prima che il Toast appaia.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                forceRestartApp()
            }, 800)
        } else {
            val msg = res.exceptionOrNull()?.message ?: getString(R.string.backup_import_fail)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private val dotColors = listOf(
        SettingsRepository.DOT_DEFAULT,
        Color.parseColor("#FF5252"),
        Color.parseColor("#FFD740"),
        Color.parseColor("#69F0AE"),
        Color.parseColor("#40C4FF"),
        Color.parseColor("#E040FB")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v19: forza full-screen senza wallpaper sotto
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)
        window.navigationBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val info = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Throwable) { null }
        val versionName = info?.versionName ?: "1.0"
        val versionCode = info?.longVersionCode ?: 1L
        binding.versionValue.text = getString(R.string.version_value, versionName)

        // v19: icona dell'app nella sezione info
        try {
            binding.appIconInfo.setImageDrawable(packageManager.getApplicationIcon(packageName))
        } catch (_: Throwable) {}

        // v71: Grid → modal
        binding.itemGridSize.setOnClickListener { showGridSizeDialog() }
        settings.gridCols.observe(this) { updateGridLabel() }
        settings.gridRows.observe(this) { updateGridLabel() }
        updateGridLabel()
        binding.itemIconShape.setOnClickListener { showIconShapeDialog() }
        binding.itemAnimStyle.setOnClickListener { showAnimStyleDialog() }

        // v75: Icon Pack (sperimentale)
        binding.itemIconPack.setOnClickListener { showIconPackDialog() }
        settings.iconPackPackage.observe(this) { updateIconPackLabel() }
        updateIconPackLabel()

        buildDotColorPicker()
        binding.itemSearchMode.setOnClickListener { showSearchModeDialog() }
        binding.itemDrawerLayout.setOnClickListener { showDrawerLayoutDialog() }
        binding.itemFolderBg.setOnClickListener { showFolderBgDialog() }
        binding.itemBadgeMode.setOnClickListener { showBadgeModeDialog() }

        binding.switchShowWidget.isChecked = settings.showWidgetSlot.value == true
        binding.switchShowWidget.setOnCheckedChangeListener { _, c -> settings.setShowWidgetSlot(c) }
        // v72: enabled/disabled delle card widget dipendenti
        settings.showWidgetSlot.observe(this) { enabled ->
            applyWidgetDependentEnabled(enabled == true)
        }
        applyWidgetDependentEnabled(settings.showWidgetSlot.value == true)

        // v85: drawer enabled toggle + reset auto grid
        binding.switchDrawerEnabled.isChecked = settings.drawerEnabled.value != false
        binding.switchDrawerEnabled.setOnCheckedChangeListener { _, on ->
            settings.setDrawerEnabled(on)
            handleDrawerToggle(on)
        }
        settings.drawerEnabled.observe(this) { applyDrawerDependentVisibility(it != false) }
        applyDrawerDependentVisibility(settings.drawerEnabled.value != false)

        binding.itemAutoGridReset.setOnClickListener { showAutoGridResetDialog() }

        // v88: auto-add nuove app installate
        binding.switchAutoAddNewApps.isChecked = settings.autoAddNewApps.value == true
        binding.switchAutoAddNewApps.setOnCheckedChangeListener { _, on ->
            settings.setAutoAddNewApps(on)
        }

        // v63: toggle "mostra barra ricerca"
        binding.switchShowSearchbar.isChecked = settings.showSearchBar.value != false
        binding.switchShowSearchbar.setOnCheckedChangeListener { _, isChecked ->
            settings.setShowSearchBar(isChecked)
        }
        // v71: enabled/disabled delle voci dipendenti dalla searchbar
        settings.showSearchBar.observe(this) { enabled ->
            applySearchBarDependentEnabled(enabled != false)
        }
        applySearchBarDependentEnabled(settings.showSearchBar.value != false)
        binding.switchHaptic.isChecked = settings.hapticEnabled.value == true
        binding.switchHaptic.setOnCheckedChangeListener { _, c -> settings.setHapticEnabled(c) }

        // v68: observers per aggiornamento subtitle in tempo reale
        settings.iconShape.observe(this) { updateShapeLabel() }
        settings.animationStyle.observe(this) { updateAnimLabel() }
        settings.searchMode.observe(this) { updateSearchModeLabel() }
        settings.drawerLayout.observe(this) { updateDrawerLayoutLabel() }
        settings.folderBgStyle.observe(this) { updateFolderBgLabel() }
        settings.notificationBadgeMode.observe(this) { updateBadgeLabel() }
        settings.recommendedPosition.observe(this) { updateRecPosLabel() }
        settings.recommendedCount.observe(this) { updateRecCountLabel() }
        // Già presenti come label di tema
        settings.searchTheme.observe(this) { updateSearchThemeLabel() }
        settings.drawerTheme.observe(this) { updateDrawerThemeLabel() }
        settings.dockTheme.observe(this) { updateDockThemeLabel() }
        settings.widgetTheme.observe(this) { updateWidgetThemeLabel() }
        // Inizializzo i label
        updateShapeLabel()
        updateAnimLabel()
        updateSearchModeLabel()
        updateDrawerLayoutLabel()
        updateFolderBgLabel()
        updateBadgeLabel()
        updateRecPosLabel()
        updateRecCountLabel()
        binding.switchSwipeDown.isChecked = settings.swipeDownNotifications.value == true
        binding.switchSwipeDown.setOnCheckedChangeListener { _, c -> settings.setSwipeDownNotifications(c) }
        binding.switchDoubleTapLock.isChecked = settings.doubleTapLock.value == true
        binding.switchDoubleTapLock.setOnCheckedChangeListener { _, c ->
            settings.setDoubleTapLock(c)
            if (c) org.cheipstudio.speedlauncher.ui.ScreenLockHelper.lockScreen(this)
        }

        binding.itemResetLayout.setOnClickListener {
            MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_reset_layout)
                .setMessage(R.string.settings_reset_layout_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetHomeLayout(); forceRestartApp()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemResetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_reset_settings)
                .setMessage(R.string.settings_reset_settings_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetSettings(); forceRestartApp()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemResetEverything.setOnClickListener {
            MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_reset_everything)
                .setMessage(R.string.settings_reset_everything_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetEverything()
                    // v60: force restart del processo per applicare reset totale
                    forceRestartApp()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemShowTutorial.setOnClickListener { settings.resetTutorial(); forceRestartApp() }

        binding.itemDefaultLauncher.setOnClickListener {
            try { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) } catch (_: Throwable) {}
        }
        binding.itemNotificationAccess.setOnClickListener {
            try { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Throwable) {}
        }
        binding.itemAppInfo.setOnClickListener {
            try {
                val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Throwable) {}
        }
        // v27: hidden apps screen
        binding.itemHiddenApps.setOnClickListener {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        // v27: backup export
        binding.itemBackupExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, BackupManager.suggestedFilename())
            }
            try { exportLauncher.launch(intent) } catch (_: Throwable) {
                Toast.makeText(this, R.string.backup_export_fail, Toast.LENGTH_SHORT).show()
            }
        }
        // v27: backup import
        binding.itemBackupImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try { importLauncher.launch(intent) } catch (_: Throwable) {
                Toast.makeText(this, R.string.backup_import_fail, Toast.LENGTH_SHORT).show()
            }
        }

        binding.itemCheipStudio.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cheipstudio.org"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Throwable) {}
        }
        // v28: contact email
        // v30: AI launcher mode toggle
        binding.switchAiMode.isChecked = settings.aiLauncherMode.value == true
        binding.switchAiMode.setOnCheckedChangeListener { _, on ->
            settings.setAiLauncherMode(on)
        }
        // v30: landscape allowed toggle
        binding.switchLandscape.isChecked = settings.landscapeAllowed.value == true
        binding.switchLandscape.setOnCheckedChangeListener { _, on ->
            settings.setLandscapeAllowed(on)
        }
        binding.itemRecommendedPosition.setOnClickListener { showRecommendedPositionDialog() }
        binding.itemRecommendedCount.setOnClickListener { showRecommendedCountDialog() }
        // v84: modalità raccomandate
        binding.itemRecommendedMode.setOnClickListener { showRecommendedModeDialog() }
        settings.recommendedMode.observe(this) { updateRecommendedModeLabel() }
        updateRecommendedModeLabel()

        // v38: language picker
        updateLanguageLabel()
        binding.itemLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // v88: slider con clamp ai bounds + sanitize del valore corrente
        // (un valore salvato fuori range causava il "random" visivo)
        val currentDim = (settings.wallpaperDim.value ?: 0).coerceIn(0, 100)
        // step=5: forzo allineamento alla griglia per evitare valori sporchi
        val safeDim = ((currentDim / 5) * 5).coerceIn(0, 100)
        binding.wallpaperDimSlider.value = safeDim.toFloat()
        updateDimLabel()
        binding.wallpaperDimSlider.addOnChangeListener { _, value, fromUser ->
            // v88: applica solo se è l\'utente a slidare (evita loop di setup)
            if (!fromUser) return@addOnChangeListener
            val v = value.toInt()
            settings.setWallpaperDim(v)
            updateDimLabel()
        }

        // v41: wallpaper blur slider (radius 0..50, step 2)
        val currentBlur = (settings.wallpaperBlur.value ?: 0).coerceIn(0, 50)
        val safeBlur = ((currentBlur / 2) * 2).coerceIn(0, 50)
        binding.wallpaperBlurSlider.value = safeBlur.toFloat()
        updateBlurLabel()
        binding.wallpaperBlurSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val v = value.toInt()
            settings.setWallpaperBlur(v)
            updateBlurLabel()
        }

        // === v51: CAMBIA SFONDO ===
        binding.itemChangeWallpaper.setOnClickListener {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                startActivity(android.content.Intent.createChooser(intent,
                    getString(R.string.settings_change_wallpaper)))
            } catch (_: Throwable) {}
        }

        // v59: Pulitore memoria AI
        binding.switchMemoryCleaner.isChecked = settings.memoryCleanerEnabled.value == true
        binding.switchMemoryCleaner.setOnCheckedChangeListener { _, isChecked ->
            settings.setMemoryCleanerEnabled(isChecked)
        }


        // === v48: TEMA SEARCH + TEMA DOCK ===
        updateSearchThemeLabel()
        binding.itemSearchTheme.setOnClickListener { showSearchThemeDialog() }

        updateDockThemeLabel()
        binding.itemDockTheme.setOnClickListener { showDockThemeDialog() }

        // === v57: TEMA DRAWER ===
        updateDrawerThemeLabel()
        binding.itemDrawerTheme.setOnClickListener { showDrawerThemeDialog() }

        // === v47: WIDGET SPEED STATS ===
        updateWidgetThemeLabel()
        binding.itemWidgetTheme.setOnClickListener { showWidgetThemeDialog() }

        binding.switchWidgetAutoRefresh.isChecked = settings.widgetAutoRefresh.value == true
        binding.switchWidgetAutoRefresh.setOnCheckedChangeListener { _, on ->
            settings.setWidgetAutoRefresh(on)
            org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider.refreshAll(this)
        }

        binding.itemContact.setOnClickListener {
            try {
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                } catch (_: Throwable) { "" }
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
                val body = "\n\n---\nSpeed Launcher v$versionName\n$device"
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:cheipstudio@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_email_subject))
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Throwable) {
                Toast.makeText(this, "cheipstudio@gmail.com", Toast.LENGTH_LONG).show()
            }
        }

        // v80: check for update
        binding.itemCheckUpdate.setOnClickListener { handleCheckUpdate() }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** v19: pallini più piccoli (28dp invece di 40) */
    private fun buildDotColorPicker() {
        binding.dotColorRow.removeAllViews()
        val density = resources.displayMetrics.density
        val current = settings.dotColor.value ?: SettingsRepository.DOT_DEFAULT
        for (color in dotColors) {
            val isSelected = color == current
            val sizePx = (28 * density).toInt()
            val btn = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginEnd = (10 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (isSelected) setStroke((2 * density).toInt(), Color.WHITE)
                    else setStroke((1 * density).toInt(), Color.parseColor("#22000000"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    settings.setDotColor(color)
                    buildDotColorPicker()
                }
            }
            binding.dotColorRow.addView(btn)
        }
    }

    private fun updateGridLabel() {
        val text = when {
            settings.gridCols.value == 4 -> getString(R.string.settings_grid_4x4)
            settings.gridCols.value == 5 && settings.gridRows.value == 5 -> getString(R.string.settings_grid_5x5)
            else -> getString(R.string.settings_grid_5x6)
        }
        binding.gridValueLabel.text = text
    }

    private fun updateShapeLabel() {
        val text = when (settings.iconShape.value) {
            SettingsRepository.SHAPE_SQUIRCLE -> getString(R.string.shape_squircle)
            SettingsRepository.SHAPE_CIRCLE -> getString(R.string.shape_circle)
            SettingsRepository.SHAPE_SQUARE -> getString(R.string.shape_square)
            SettingsRepository.SHAPE_TEARDROP -> getString(R.string.shape_teardrop)
            else -> getString(R.string.shape_original)
        }
        binding.iconShapeLabel.text = text
    }

    private fun updateAnimLabel() {
        val text = when (settings.animationStyle.value) {
            SettingsRepository.ANIM_STANDARD -> getString(R.string.anim_standard)
            SettingsRepository.ANIM_FAST -> getString(R.string.anim_fast)
            SettingsRepository.ANIM_NONE -> getString(R.string.anim_none)
            else -> getString(R.string.anim_expressive)
        }
        binding.animStyleLabel.text = text
    }

    private fun updateRecPosLabel() {
        val text = when (settings.recommendedPosition.value) {
            SettingsRepository.REC_POS_BOTTOM -> getString(R.string.recommended_pos_bottom)
            else -> getString(R.string.recommended_pos_top)
        }
        binding.recommendedPositionLabel.text = text
    }

    private fun updateRecCountLabel() {
        val n = settings.recommendedCount.value ?: 5
        binding.recommendedCountLabel.text = if (n == 4)
            getString(R.string.recommended_count_4) else getString(R.string.recommended_count_5)
    }

    private fun updateLanguageLabel() {
        val code = settings.language.value ?: "auto"
        binding.languageLabel.text = LanguageHelper.displayNameFor(code)
    }

    private fun updateDimLabel() {
        val v = settings.wallpaperDim.value ?: 0
        binding.wallpaperDimLabel.text = "$v%"
    }

    private fun updateBlurLabel() {
        val v = settings.wallpaperBlur.value ?: 0
        binding.wallpaperBlurLabel.text = if (v == 0) getString(android.R.string.no) else "${v}px"
    }

    /** v47: widget theme label + dialog */
    private fun updateWidgetThemeLabel() {
        val current = settings.widgetTheme.value ?: "system"
        val labelRes = when (current) {
            "transparent" -> R.string.widget_theme_transparent
            "light" -> R.string.widget_theme_light
            "dark" -> R.string.widget_theme_dark
            else -> R.string.widget_theme_system
        }
        binding.widgetThemeValue.text = getString(labelRes)
    }

    private fun updateSearchThemeLabel() {
        val current = settings.searchTheme.value ?: "system"
        binding.searchThemeValue.text = themeLabel(current)
    }

    private fun showSearchThemeDialog() {
        showGenericThemeDialog(R.string.settings_search_theme,
            settings.searchTheme.value ?: "system") { picked ->
            settings.setSearchTheme(picked)
            updateSearchThemeLabel()
        }
    }

    private fun updateDrawerThemeLabel() {
        val current = settings.drawerTheme.value ?: "system"
        binding.drawerThemeValue.text = themeLabel(current)
    }

    private fun showDrawerThemeDialog() {
        showGenericThemeDialog(R.string.settings_drawer_theme,
            settings.drawerTheme.value ?: "system") { picked ->
            settings.setDrawerTheme(picked)
            updateDrawerThemeLabel()
        }
    }

    private fun updateDockThemeLabel() {
        val current = settings.dockTheme.value ?: "system"
        binding.dockThemeValue.text = themeLabel(current)
    }

    private fun showDockThemeDialog() {
        showGenericThemeDialog(R.string.settings_dock_theme,
            settings.dockTheme.value ?: "system") { picked ->
            settings.setDockTheme(picked)
            updateDockThemeLabel()
        }
    }

    private fun themeLabel(key: String): String {
        return getString(when (key) {
            "transparent" -> R.string.widget_theme_transparent
            "light" -> R.string.widget_theme_light
            "dark" -> R.string.widget_theme_dark
            else -> R.string.widget_theme_system
        })
    }

    private fun showGenericThemeDialog(titleRes: Int, current: String, onPicked: (String) -> Unit) {
        val labels = arrayOf(
            getString(R.string.widget_theme_system),
            getString(R.string.widget_theme_transparent),
            getString(R.string.widget_theme_light),
            getString(R.string.widget_theme_dark)
        )
        val keys = arrayOf("system", "transparent", "light", "dark")
        val sel = keys.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(labels, sel) { dialog, which ->
                onPicked(keys[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * v60: termina il processo e riavvia l\'app dalla MainActivity.
     * Necessario dopo reset totale per ricaricare tutte le impostazioni in stato fresh.
     */


    /** v83: check + download + install update (via GitHub Releases) */
    private fun handleCheckUpdate() {
        // v121: con repo privata l'API GitHub non risponde. Apro direttamente
        // la pagina releases nel browser, l'utente sceglie e scarica l'APK.
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/cheipstudio-cmyk/speed-launcher/releases")
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(this, "Browser non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUpdateAvailableDialog(info: org.cheipstudio.speedlauncher.tools.UpdateChecker.UpdateInfo) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.update_available_title)
            .setMessage(getString(
                R.string.update_available_msg,
                info.latestVersion,
                info.currentVersion,
                info.releaseNotes.take(300)
            ))
            .setPositiveButton(R.string.update_download) { _, _ ->
                if (info.downloadUrl != null) startUpdateDownload(info.downloadUrl)
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun startUpdateDownload(url: String) {
        // v83: dialog con progress bar durante download, poi apre installer di sistema
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.update_downloading_title)
            .setMessage(getString(R.string.update_downloading_progress, 0))
            .setCancelable(false)
            .create()
        dialog.show()

        org.cheipstudio.speedlauncher.tools.UpdateChecker.downloadAndInstall(
            this,
            url,
            onProgress = { downloaded, total ->
                if (total > 0) {
                    val pct = (downloaded * 100 / total).toInt()
                    dialog.setMessage(getString(R.string.update_downloading_progress, pct))
                } else {
                    val mb = downloaded / (1024 * 1024)
                    dialog.setMessage(getString(R.string.update_downloading_size, mb))
                }
            },
            onComplete = { file ->
                dialog.dismiss()
                if (file == null) {
                    Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
                // Se OK, l'installer parte automaticamente
            }
        )
    }




    /**
     * v85: visibilità del bottone "Reset griglia" — solo se drawer disabilitato.
     */
    private fun applyDrawerDependentVisibility(drawerEnabled: Boolean) {
        binding.itemAutoGridReset.visibility = if (drawerEnabled) android.view.View.GONE else android.view.View.VISIBLE
    }

    /**
     * v85: gestisce il toggle drawer.
     * - ON → drawer = true: rimuovo le app autoAdded dalla home (mantenendo personalizzazioni)
     * - OFF → drawer = false: popolo la home con tutte le app non già presenti
     */
    private fun handleDrawerToggle(drawerEnabled: Boolean) {
        val store = org.cheipstudio.speedlauncher.data.HomeLayoutStore(this)
        if (drawerEnabled) {
            org.cheipstudio.speedlauncher.tools.HomeAutoPopulator.removeAutoAdded(store)
            forceRestartApp()
        } else {
            val cols = settings.gridCols.value ?: 4
            val rows = settings.gridRows.value ?: 5
            val appRepo = org.cheipstudio.speedlauncher.SpeedApp.instance.appRepository
            val current = appRepo.apps.value

            // v116: logica semplificata e diagnostica
            // Se le app sono già caricate, popola subito.
            if (current != null && current.size > 5) {
                val before = store.load().size
                org.cheipstudio.speedlauncher.tools.HomeAutoPopulator.populate(store, cols, rows)
                val after = store.load().size
                Toast.makeText(this, "App aggiunte: ${after - before} (totale: $after)", Toast.LENGTH_LONG).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    forceRestartApp()
                }, 1200)
            } else {
                // App non caricate. Forzo reload sincrono e aspetto.
                Toast.makeText(this, "Caricamento app in corso...", Toast.LENGTH_SHORT).show()
                Thread {
                    // Forza reload sincrono delle app (lo carico io qui)
                    val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                    val activities = try {
                        launcherApps.getActivityList(null, android.os.Process.myUserHandle())
                    } catch (_: Throwable) { emptyList() }
                    
                    // Costruisco la lista di chiavi
                    val keys = activities.map { 
                        "${it.applicationInfo.packageName}/${it.componentName.className}" 
                    }
                    
                    runOnUiThread {
                        if (keys.isEmpty()) {
                            Toast.makeText(this, "Errore: nessuna app trovata", Toast.LENGTH_LONG).show()
                            forceRestartApp()
                            return@runOnUiThread
                        }
                        
                        // Popolo manualmente con queste chiavi
                        populateWithKeys(store, keys, cols, rows)
                        Toast.makeText(this, "App aggiunte: ${keys.size}", Toast.LENGTH_LONG).show()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            forceRestartApp()
                        }, 1200)
                    }
                }.start()
            }
        }
    }

    /** v116: popolamento manuale (bypass HomeAutoPopulator se app non sono pronte) */
    private fun populateWithKeys(
        store: org.cheipstudio.speedlauncher.data.HomeLayoutStore,
        keys: List<String>,
        cols: Int,
        rows: Int
    ) {
        val current = store.load().toMutableList()
        val presentKeys = current.flatMap {
            if (it.type == org.cheipstudio.speedlauncher.data.HomeItem.TYPE_FOLDER)
                it.folderApps + it.key
            else listOf(it.key)
        }.toSet()

        val toAdd = keys.filter { it !in presentKeys }
        if (toAdd.isEmpty()) return

        val occupiedByPage = current.groupBy { it.page }
            .mapValues { (_, items) -> items.map { it.cellX to it.cellY }.toMutableSet() }
            .toMutableMap()

        val queue = ArrayDeque(toAdd)
        var page = 0
        while (queue.isNotEmpty() && page <= 50) {
            val occupied = occupiedByPage.getOrPut(page) { mutableSetOf() }
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (queue.isEmpty()) break
                    if (occupied.contains(x to y)) continue
                    val key = queue.removeFirst()
                    current.add(org.cheipstudio.speedlauncher.data.HomeItem(
                        key = key,
                        page = page,
                        cellX = x,
                        cellY = y,
                        type = org.cheipstudio.speedlauncher.data.HomeItem.TYPE_APP,
                        autoAdded = true
                    ))
                    occupied.add(x to y)
                }
                if (queue.isEmpty()) break
            }
            page++
        }
        store.save(current)
    }

    /** v85: dialog di conferma + reset griglia automatica */
    private fun showAutoGridResetDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.auto_grid_reset_confirm_title)
            .setMessage(R.string.auto_grid_reset_confirm_msg)
            .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                val store = org.cheipstudio.speedlauncher.data.HomeLayoutStore(this)
                val cols = settings.gridCols.value ?: 4
                val rows = settings.gridRows.value ?: 5
                org.cheipstudio.speedlauncher.tools.HomeAutoPopulator.fullReset(store, cols, rows)
                Toast.makeText(this, R.string.auto_grid_reset_done, Toast.LENGTH_SHORT).show()
                forceRestartApp()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun forceRestartApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finishAffinity()
            Runtime.getRuntime().exit(0)
        } catch (_: Throwable) {
            finishAffinity()
            Runtime.getRuntime().exit(0)
        }
    }

    private fun showWidgetThemeDialog() {
        val labels = arrayOf(
            getString(R.string.widget_theme_system),
            getString(R.string.widget_theme_transparent),
            getString(R.string.widget_theme_light),
            getString(R.string.widget_theme_dark)
        )
        val keys = arrayOf("system", "transparent", "light", "dark")
        val current = settings.widgetTheme.value ?: "system"
        val sel = keys.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_widget_theme)
            .setSingleChoiceItems(labels, sel) { dialog, which ->
                settings.setWidgetTheme(keys[which])
                updateWidgetThemeLabel()
                org.cheipstudio.speedlauncher.widgets.SpeedStatsWidgetProvider.refreshAll(this)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val items = LanguageHelper.SUPPORTED_LANGUAGES
        val labels = items.map { it.second }.toTypedArray()
        val codes = items.map { it.first }
        val current = settings.language.value ?: "auto"
        val checkedIndex = codes.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val code = codes[which]
                settings.setLanguage(code)
                LanguageHelper.applyLanguage(code)
                updateLanguageLabel()
                dialog.dismiss()
                // recreate per applicare la lingua
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateBadgeLabel() {
        val text = when (settings.notificationBadgeMode.value) {
            SettingsRepository.BADGE_COUNT -> getString(R.string.badge_count)
            SettingsRepository.BADGE_OFF -> getString(R.string.badge_off)
            else -> getString(R.string.badge_dot)
        }
        binding.badgeModeLabel.text = text
    }

    private fun updateFolderBgLabel() {
        val text = when (settings.folderBgStyle.value) {
            SettingsRepository.FOLDER_BG_TRANSPARENT -> getString(R.string.folder_bg_transparent)
            SettingsRepository.FOLDER_BG_DARK -> getString(R.string.folder_bg_dark)
            SettingsRepository.FOLDER_BG_LIGHT -> getString(R.string.folder_bg_light)
            else -> getString(R.string.folder_bg_system)
        }
        binding.folderBgLabel.text = text
    }

    private fun updateSearchModeLabel() {
        val v = settings.searchMode.value
        binding.searchModeLabel.text = when (v) {
            SettingsRepository.MODE_APPS -> getString(R.string.settings_search_apps)
            SettingsRepository.MODE_GOOGLE -> getString(R.string.settings_search_google)
            else -> getString(R.string.settings_search_apps)
        }
    }

    private fun updateDrawerLayoutLabel() {
        val v = settings.drawerLayout.value
        binding.drawerLayoutLabel.text = when (v) {
            SettingsRepository.DRAWER_GRID3 -> getString(R.string.drawer_grid3)
            SettingsRepository.DRAWER_GRID4 -> getString(R.string.drawer_grid4)
            SettingsRepository.DRAWER_GRID5 -> getString(R.string.drawer_grid5)
            SettingsRepository.DRAWER_LIST -> getString(R.string.drawer_list)
            else -> getString(R.string.drawer_list)
        }
    }


    private fun showGridSizeDialog() {
        val cols = settings.gridCols.value ?: 4
        val rows = settings.gridRows.value ?: 5
        val current = "${cols}x${rows}"
        val options = listOf("4x4","4x5","4x6","5x5","5x6","6x5","6x6")
        val labels = options.map {
            val parts = it.split("x")
            "${parts[0]} × ${parts[1]}"
        }.toTypedArray()
        val sel = options.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.settings_grid_size)
            .setSingleChoiceItems(labels, sel) { dialog, which ->
                val parts = options[which].split("x")
                settings.setGrid(parts[0].toInt(), parts[1].toInt())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }




    private fun updateRecommendedModeLabel() {
        val mode = settings.recommendedMode.value ?: SettingsRepository.REC_MODE_AI
        binding.recommendedModeLabel.text = when (mode) {
            SettingsRepository.REC_MODE_MANUAL -> getString(R.string.rec_mode_manual)
            else -> getString(R.string.rec_mode_ai)
        }
    }

    /** v84: dialog scelta AI vs Manuale per le raccomandate */
    private fun showRecommendedModeDialog() {
        val current = settings.recommendedMode.value ?: SettingsRepository.REC_MODE_AI
        val labels = arrayOf(
            getString(R.string.rec_mode_ai),
            getString(R.string.rec_mode_manual)
        )
        val values = arrayOf(SettingsRepository.REC_MODE_AI, SettingsRepository.REC_MODE_MANUAL)
        val sel = values.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.settings_recommended_mode)
            .setSingleChoiceItems(labels, sel) { dialog, which ->
                val picked = values[which]
                settings.setRecommendedMode(picked)
                dialog.dismiss()
                if (picked == SettingsRepository.REC_MODE_MANUAL) {
                    // Apro selettore app per scegliere quali mostrare
                    showRecommendedManualPicker()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** v84: dialog multi-select per scegliere le app raccomandate manuali */
    private fun showRecommendedManualPicker() {
        val apps = org.cheipstudio.speedlauncher.SpeedApp.instance.appRepository.apps.value
            ?: emptyList()
        if (apps.isEmpty()) {
            // v86: se appRepository non ha ancora caricato (race condition),
            // ritento dopo 300ms invece che mostrare lista vuota.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showRecommendedManualPicker()
            }, 300)
            return
        }
        val hidden = settings.hiddenApps.value ?: emptySet<String>()
        val available = apps.filter { !hidden.contains(it.key) }
            .sortedBy { it.label.lowercase() }
        val countNeeded = settings.recommendedCount.value ?: 5
        val current = settings.recommendedManualApps.value ?: mutableSetOf()

        val labels = available.map { it.label }.toTypedArray()
        val checked = BooleanArray(available.size) { i -> current.contains(available[i].key) }
        val selected = current.toMutableSet()

        // v86: niente setMessage — su alcune versioni Material il messaggio
        // mangia la lista. Metto il count nel titolo direttamente.
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(getString(R.string.rec_mode_pick_apps_title, countNeeded))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val key = available[which].key
                if (isChecked) selected.add(key) else selected.remove(key)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected.size == countNeeded) {
                    settings.setRecommendedManualApps(selected)
                    Toast.makeText(this, getString(R.string.rec_mode_pick_apps_title, countNeeded), Toast.LENGTH_SHORT).show()
                } else {
                    // Salvo comunque ma avviso
                    settings.setRecommendedManualApps(selected)
                    if (selected.isEmpty()) {
                        Toast.makeText(this, R.string.rec_mode_no_apps_selected, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.rec_mode_pick_apps_too_few, countNeeded),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** v71: abilita/disabilita visivamente le card che dipendono dalla searchbar */
    private fun applySearchBarDependentEnabled(enabled: Boolean) {
        binding.itemSearchMode.isEnabled = enabled
        binding.itemSearchMode.alpha = if (enabled) 1.0f else 0.45f
        binding.itemSearchTheme.isEnabled = enabled
        binding.itemSearchTheme.alpha = if (enabled) 1.0f else 0.45f
    }

    /** v72: abilita/disabilita visivamente le card widget dipendenti dal toggle "Mostra widget" */
    private fun applyWidgetDependentEnabled(enabled: Boolean) {
        binding.itemWidgetTheme.isEnabled = enabled
        binding.itemWidgetTheme.alpha = if (enabled) 1.0f else 0.45f
        binding.itemWidgetAutoRefresh.isEnabled = enabled
        binding.itemWidgetAutoRefresh.alpha = if (enabled) 1.0f else 0.45f
        binding.switchWidgetAutoRefresh.isEnabled = enabled
    }



    private fun updateIconPackLabel() {
        val pkg = settings.iconPackPackage.value ?: ""
        binding.iconPackLabel.text = if (pkg.isEmpty()) {
            getString(R.string.icon_pack_none)
        } else {
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Throwable) {
                getString(R.string.icon_pack_none)
            }
        }
    }

    private fun showIconPackDialog() {
        val packs = org.cheipstudio.speedlauncher.tools.IconPackManager
            .listInstalledIconPacks(this)
        if (packs.isEmpty()) {
            // Nessun icon pack installato
            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            )
                .setTitle(R.string.icon_pack_no_pack_installed)
                .setMessage(R.string.icon_pack_no_pack_installed_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        // Lista: "Nessuno" + tutti gli icon pack
        val labels = mutableListOf(getString(R.string.icon_pack_none))
        labels.addAll(packs.map { it.name })
        val values = mutableListOf("")
        values.addAll(packs.map { it.packageName })
        val current = settings.iconPackPackage.value ?: ""
        val sel = values.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.settings_icon_pack)
            .setSingleChoiceItems(labels.toTypedArray(), sel) { dialog, which ->
                val picked = values[which]
                if (picked != current) {
                    settings.setIconPackPackage(picked)
                    Toast.makeText(this, R.string.icon_pack_applied, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // v75: restart per applicare l'icon pack a tutte le icone
                    forceRestartApp()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ============================================================
    // v68: dialog selettori (sostituiscono i RadioGroup)
    // ============================================================

    /**
     * Helper generico: mostra dialog con scelta singola tra opzioni.
     * @param titleRes resource del titolo
     * @param labels lista delle label da mostrare
     * @param values lista valori (parallela a labels) da passare a onPicked
     * @param current valore attualmente selezionato
     * @param onPicked callback con il valore scelto
     */
    private fun <T> showSelectionDialog(
        titleRes: Int,
        labels: Array<String>,
        values: Array<T>,
        current: T,
        onPicked: (T) -> Unit
    ) {
        val sel = values.indexOf(current).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(titleRes)
            .setSingleChoiceItems(labels, sel) { dialog, which ->
                onPicked(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showIconShapeDialog() {
        showSelectionDialog(
            R.string.settings_icon_shape,
            arrayOf(
                getString(R.string.shape_original),
                getString(R.string.shape_circle),
                getString(R.string.shape_squircle),
                getString(R.string.shape_square),
                getString(R.string.shape_teardrop)
            ),
            arrayOf(
                SettingsRepository.SHAPE_ORIGINAL,
                SettingsRepository.SHAPE_CIRCLE,
                SettingsRepository.SHAPE_SQUIRCLE,
                SettingsRepository.SHAPE_SQUARE,
                SettingsRepository.SHAPE_TEARDROP
            ),
            settings.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL
        ) { settings.setIconShape(it) }
    }

    private fun showAnimStyleDialog() {
        showSelectionDialog(
            R.string.settings_anim_style,
            arrayOf(
                getString(R.string.anim_expressive),
                getString(R.string.anim_standard),
                getString(R.string.anim_fast),
                getString(R.string.anim_none)
            ),
            arrayOf(
                SettingsRepository.ANIM_EXPRESSIVE,
                SettingsRepository.ANIM_STANDARD,
                SettingsRepository.ANIM_FAST,
                SettingsRepository.ANIM_NONE
            ),
            settings.animationStyle.value ?: SettingsRepository.ANIM_EXPRESSIVE
        ) { settings.setAnimationStyle(it) }
    }

    private fun showSearchModeDialog() {
        showSelectionDialog(
            R.string.settings_search_mode,
            arrayOf(
                getString(R.string.settings_search_apps),
                getString(R.string.settings_search_google)
            ),
            arrayOf(
                SettingsRepository.MODE_APPS,
                SettingsRepository.MODE_GOOGLE
            ),
            settings.searchMode.value ?: SettingsRepository.MODE_APPS
        ) { settings.setSearchMode(it) }
    }


    private fun showDrawerLayoutDialog() {
        showSelectionDialog(
            R.string.settings_drawer_layout,
            arrayOf(
                getString(R.string.drawer_grid3),
                getString(R.string.drawer_grid4),
                getString(R.string.drawer_grid5),
                getString(R.string.drawer_list)
            ),
            arrayOf(
                SettingsRepository.DRAWER_GRID3,
                SettingsRepository.DRAWER_GRID4,
                SettingsRepository.DRAWER_GRID5,
                SettingsRepository.DRAWER_LIST
            ),
            settings.drawerLayout.value ?: SettingsRepository.DRAWER_LIST
        ) { settings.setDrawerLayout(it) }
    }

    private fun showFolderBgDialog() {
        showSelectionDialog(
            R.string.settings_folder_bg,
            arrayOf(
                getString(R.string.folder_bg_system),
                getString(R.string.folder_bg_transparent),
                getString(R.string.folder_bg_light),
                getString(R.string.folder_bg_dark)
            ),
            arrayOf(
                SettingsRepository.FOLDER_BG_SYSTEM,
                SettingsRepository.FOLDER_BG_TRANSPARENT,
                SettingsRepository.FOLDER_BG_LIGHT,
                SettingsRepository.FOLDER_BG_DARK
            ),
            settings.folderBgStyle.value ?: SettingsRepository.FOLDER_BG_SYSTEM
        ) { settings.setFolderBgStyle(it) }
    }

    private fun showBadgeModeDialog() {
        showSelectionDialog(
            R.string.settings_badge_mode,
            arrayOf(
                getString(R.string.badge_off),
                getString(R.string.badge_dot),
                getString(R.string.badge_count)
            ),
            arrayOf(
                SettingsRepository.BADGE_OFF,
                SettingsRepository.BADGE_DOT,
                SettingsRepository.BADGE_COUNT
            ),
            settings.notificationBadgeMode.value ?: SettingsRepository.BADGE_DOT
        ) { settings.setNotificationBadgeMode(it) }
    }

    private fun showRecommendedPositionDialog() {
        showSelectionDialog(
            R.string.settings_recommended_position,
            arrayOf(
                getString(R.string.recommended_pos_top),
                getString(R.string.recommended_pos_bottom)
            ),
            arrayOf(
                SettingsRepository.REC_POS_TOP,
                SettingsRepository.REC_POS_BOTTOM
            ),
            settings.recommendedPosition.value ?: SettingsRepository.REC_POS_BOTTOM
        ) { settings.setRecommendedPosition(it) }
    }

    private fun showRecommendedCountDialog() {
        showSelectionDialog(
            R.string.settings_recommended_count,
            arrayOf(
                getString(R.string.recommended_count_4),
                getString(R.string.recommended_count_5)
            ),
            arrayOf(4, 5),
            settings.recommendedCount.value ?: 5
        ) { settings.setRecommendedCount(it) }
    }

}
