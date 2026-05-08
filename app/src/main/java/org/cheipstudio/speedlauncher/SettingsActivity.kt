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
            // restart per applicare
            val i = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
            finish()
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

        when {
            settings.gridCols.value == 4 && settings.gridRows.value == 4 -> binding.gridRadio4x4.isChecked = true
            settings.gridCols.value == 4 && settings.gridRows.value == 5 -> binding.gridRadio4x5.isChecked = true
            settings.gridCols.value == 4 && settings.gridRows.value == 6 -> binding.gridRadio4x6.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 5 -> binding.gridRadio5x5.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 6 -> binding.gridRadio5x6.isChecked = true
            settings.gridCols.value == 6 && settings.gridRows.value == 5 -> binding.gridRadio6x5.isChecked = true
            settings.gridCols.value == 6 && settings.gridRows.value == 6 -> binding.gridRadio6x6.isChecked = true
        }
        updateGridLabel()
        binding.gridRadioGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.gridRadio4x4 -> settings.setGrid(4, 4)
                R.id.gridRadio4x5 -> settings.setGrid(4, 5)
                R.id.gridRadio4x6 -> settings.setGrid(4, 6)
                R.id.gridRadio5x5 -> settings.setGrid(5, 5)
                R.id.gridRadio5x6 -> settings.setGrid(5, 6)
                R.id.gridRadio6x5 -> settings.setGrid(6, 5)
                R.id.gridRadio6x6 -> settings.setGrid(6, 6)
            }
            updateGridLabel()
        }

        when (settings.iconShape.value) {
            SettingsRepository.SHAPE_ORIGINAL -> binding.shapeOriginal.isChecked = true
            SettingsRepository.SHAPE_SQUIRCLE -> binding.shapeSquircle.isChecked = true
            SettingsRepository.SHAPE_CIRCLE -> binding.shapeCircle.isChecked = true
            SettingsRepository.SHAPE_SQUARE -> binding.shapeSquare.isChecked = true
            SettingsRepository.SHAPE_TEARDROP -> binding.shapeTeardrop.isChecked = true
        }
        updateShapeLabel()
        binding.iconShapeGroup.setOnCheckedChangeListener { _, id ->
            val shape = when (id) {
                R.id.shapeOriginal -> SettingsRepository.SHAPE_ORIGINAL
                R.id.shapeSquircle -> SettingsRepository.SHAPE_SQUIRCLE
                R.id.shapeCircle -> SettingsRepository.SHAPE_CIRCLE
                R.id.shapeSquare -> SettingsRepository.SHAPE_SQUARE
                R.id.shapeTeardrop -> SettingsRepository.SHAPE_TEARDROP
                else -> SettingsRepository.SHAPE_ORIGINAL
            }
            settings.setIconShape(shape)
            updateShapeLabel()
        }

        when (settings.animationStyle.value) {
            SettingsRepository.ANIM_EXPRESSIVE -> binding.animExpressive.isChecked = true
            SettingsRepository.ANIM_STANDARD -> binding.animStandard.isChecked = true
            SettingsRepository.ANIM_NONE -> binding.animNone.isChecked = true
        }
        updateAnimLabel()
        binding.animStyleGroup.setOnCheckedChangeListener { _, id ->
            val style = when (id) {
                R.id.animExpressive -> SettingsRepository.ANIM_EXPRESSIVE
                R.id.animStandard -> SettingsRepository.ANIM_STANDARD
                R.id.animNone -> SettingsRepository.ANIM_NONE
                else -> SettingsRepository.ANIM_EXPRESSIVE
            }
            settings.setAnimationStyle(style)
            updateAnimLabel()
        }

        buildDotColorPicker()

        binding.searchModeApps.isChecked = settings.searchMode.value == SettingsRepository.MODE_APPS
        binding.searchModeGoogle.isChecked = settings.searchMode.value == SettingsRepository.MODE_GOOGLE
        binding.searchModeGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.searchModeApps -> settings.setSearchMode(SettingsRepository.MODE_APPS)
                R.id.searchModeGoogle -> settings.setSearchMode(SettingsRepository.MODE_GOOGLE)
            }
        }

        // v20: drawer layout
        when (settings.drawerLayout.value) {
            SettingsRepository.DRAWER_GRID3 -> binding.drawerLayoutGrid3.isChecked = true
            SettingsRepository.DRAWER_GRID4 -> binding.drawerLayoutGrid4.isChecked = true
            SettingsRepository.DRAWER_GRID5 -> binding.drawerLayoutGrid5.isChecked = true
            SettingsRepository.DRAWER_LIST -> binding.drawerLayoutList.isChecked = true
        }
        binding.drawerLayoutGroup.setOnCheckedChangeListener { _, id ->
            val layout = when (id) {
                R.id.drawerLayoutGrid3 -> SettingsRepository.DRAWER_GRID3
                R.id.drawerLayoutGrid5 -> SettingsRepository.DRAWER_GRID5
                R.id.drawerLayoutList -> SettingsRepository.DRAWER_LIST
                else -> SettingsRepository.DRAWER_GRID4
            }
            settings.setDrawerLayout(layout)
        }

        // v22: folder bg
        when (settings.folderBgStyle.value) {
            SettingsRepository.FOLDER_BG_TRANSPARENT -> binding.folderBgTransparent.isChecked = true
            SettingsRepository.FOLDER_BG_DARK -> binding.folderBgDark.isChecked = true
            SettingsRepository.FOLDER_BG_LIGHT -> binding.folderBgLight.isChecked = true
            else -> binding.folderBgSystem.isChecked = true
        }
        updateFolderBgLabel()
        binding.folderBgGroup.setOnCheckedChangeListener { _, id ->
            val style = when (id) {
                R.id.folderBgTransparent -> SettingsRepository.FOLDER_BG_TRANSPARENT
                R.id.folderBgDark -> SettingsRepository.FOLDER_BG_DARK
                R.id.folderBgLight -> SettingsRepository.FOLDER_BG_LIGHT
                else -> SettingsRepository.FOLDER_BG_SYSTEM
            }
            settings.setFolderBgStyle(style)
            updateFolderBgLabel()
        }

        // v23: badge mode
        when (settings.notificationBadgeMode.value) {
            SettingsRepository.BADGE_COUNT -> binding.badgeCount.isChecked = true
            SettingsRepository.BADGE_OFF -> binding.badgeOff.isChecked = true
            else -> binding.badgeDot.isChecked = true
        }
        updateBadgeLabel()
        binding.badgeModeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.badgeCount -> SettingsRepository.BADGE_COUNT
                R.id.badgeOff -> SettingsRepository.BADGE_OFF
                else -> SettingsRepository.BADGE_DOT
            }
            settings.setNotificationBadgeMode(mode)
            updateBadgeLabel()
        }

        when (settings.searchBarStyle.value) {
            SettingsRepository.STYLE_SYSTEM -> binding.styleSystem.isChecked = true
            SettingsRepository.STYLE_TRANSPARENT -> binding.styleTransparent.isChecked = true
            SettingsRepository.STYLE_DARK -> binding.styleDark.isChecked = true
            SettingsRepository.STYLE_LIGHT -> binding.styleLight.isChecked = true
        }
        updateStyleLabel()
        binding.searchStyleGroup.setOnCheckedChangeListener { _, id ->
            val style = when (id) {
                R.id.styleSystem -> SettingsRepository.STYLE_SYSTEM
                R.id.styleTransparent -> SettingsRepository.STYLE_TRANSPARENT
                R.id.styleDark -> SettingsRepository.STYLE_DARK
                R.id.styleLight -> SettingsRepository.STYLE_LIGHT
                else -> SettingsRepository.STYLE_SYSTEM
            }
            settings.setSearchBarStyle(style)
            updateStyleLabel()
        }

        binding.switchShowWidget.isChecked = settings.showWidgetSlot.value == true
        binding.switchShowWidget.setOnCheckedChangeListener { _, c -> settings.setShowWidgetSlot(c) }
        binding.switchHaptic.isChecked = settings.hapticEnabled.value == true
        binding.switchHaptic.setOnCheckedChangeListener { _, c -> settings.setHapticEnabled(c) }
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
                    settings.resetHomeLayout(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemResetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_reset_settings)
                .setMessage(R.string.settings_reset_settings_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetSettings(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemResetEverything.setOnClickListener {
            MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_reset_everything)
                .setMessage(R.string.settings_reset_everything_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetEverything(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        binding.itemShowTutorial.setOnClickListener { settings.resetTutorial(); finish() }

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

        // v32: recommendedPosition
        when (settings.recommendedPosition.value) {
            SettingsRepository.REC_POS_BOTTOM -> binding.recPosBottom.isChecked = true
            else -> binding.recPosTop.isChecked = true
        }
        updateRecPosLabel()
        binding.recommendedPositionGroup.setOnCheckedChangeListener { _, id ->
            val pos = when (id) {
                R.id.recPosBottom -> SettingsRepository.REC_POS_BOTTOM
                else -> SettingsRepository.REC_POS_TOP
            }
            settings.setRecommendedPosition(pos)
            updateRecPosLabel()
        }

        // v37: recommendedCount (4 o 5)
        when (settings.recommendedCount.value) {
            4 -> binding.recCount4.isChecked = true
            else -> binding.recCount5.isChecked = true
        }
        updateRecCountLabel()
        binding.recommendedCountGroup.setOnCheckedChangeListener { _, id ->
            val n = when (id) {
                R.id.recCount4 -> 4
                else -> 5
            }
            settings.setRecommendedCount(n)
            updateRecCountLabel()
        }

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

    private fun updateStyleLabel() {
        val text = when (settings.searchBarStyle.value) {
            SettingsRepository.STYLE_TRANSPARENT -> getString(R.string.style_transparent)
            SettingsRepository.STYLE_DARK -> getString(R.string.style_dark)
            SettingsRepository.STYLE_LIGHT -> getString(R.string.style_light)
            else -> getString(R.string.style_system)
        }
        binding.styleValueLabel.text = text
    }
}
