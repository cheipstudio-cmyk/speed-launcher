package org.cheipstudio.speedlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.cheipstudio.speedlauncher.data.SettingsRepository
import org.cheipstudio.speedlauncher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = SpeedApp.instance.settingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val info = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Throwable) { null }
        val versionName = info?.versionName ?: "1.0"
        val versionCode = info?.longVersionCode ?: 1L
        binding.versionValue.text = getString(R.string.version_value, versionName)
        binding.buildValue.text = getString(R.string.build_value, versionCode.toString())

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

        binding.itemResetLayout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_layout)
                .setMessage(R.string.settings_reset_layout_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetHomeLayout(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.itemResetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_settings)
                .setMessage(R.string.settings_reset_settings_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetSettings(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.itemResetEverything.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_everything)
                .setMessage(R.string.settings_reset_everything_msg)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    settings.resetEverything(); finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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

    private fun updateGridLabel() {
        val text = when {
            settings.gridCols.value == 4 -> getString(R.string.settings_grid_4x4)
            settings.gridCols.value == 5 && settings.gridRows.value == 5 -> getString(R.string.settings_grid_5x5)
            else -> getString(R.string.settings_grid_5x6)
        }
        binding.gridValueLabel.text = text
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
