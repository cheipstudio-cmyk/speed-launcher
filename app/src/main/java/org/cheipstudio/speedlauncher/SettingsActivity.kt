package org.cheipstudio.speedlauncher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
        binding.buildValue.text = getString(R.string.build_value, versionCode.toString())

        // v19: icona dell'app nella sezione info
        try {
            binding.appIconInfo.setImageDrawable(packageManager.getApplicationIcon(packageName))
        } catch (_: Throwable) {}

        when {
            settings.gridCols.value == 4 && settings.gridRows.value == 4 -> binding.gridRadio4x4.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 5 -> binding.gridRadio5x5.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 6 -> binding.gridRadio5x6.isChecked = true
        }
        updateGridLabel()
        binding.gridRadioGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.gridRadio4x4 -> settings.setGrid(4, 4)
                R.id.gridRadio5x5 -> settings.setGrid(5, 5)
                R.id.gridRadio5x6 -> settings.setGrid(5, 6)
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
        binding.itemCheipStudio.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cheipstudio.org"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Throwable) {}
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
