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
            // v72: force restart per applicare il backup
            forceRestartApp()
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

        // v38: language picker
        updateLanguageLabel()
        binding.itemLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // v38: wallpaper dim slider
        val currentDim = settings.wallpaperDim.value ?: 0
        binding.wallpaperDimSlider.value = currentDim.toFloat()
        updateDimLabel()
        binding.wallpaperDimSlider.addOnChangeListener { _, value, _ ->
            settings.setWallpaperDim(value.toInt())
            updateDimLabel()
        }

        // v41: wallpaper blur slider (radius 0..50)
        val currentBlur = settings.wallpaperBlur.value ?: 0
        binding.wallpaperBlurSlider.value = currentBlur.toFloat()
        updateBlurLabel()
        binding.wallpaperBlurSlider.addOnChangeListener { _, value, _ ->
            settings.setWallpaperBlur(value.toInt())
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
                getString(R.string.anim_none)
            ),
            arrayOf(
                SettingsRepository.ANIM_EXPRESSIVE,
                SettingsRepository.ANIM_STANDARD,
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
