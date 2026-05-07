package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
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

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) { "1.0" }
        binding.versionValue.text = getString(R.string.version_value, versionName)

        // Grid radio + label dinamica
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

        binding.switchShowWidget.isChecked = settings.showWidgetSlot.value == true
        binding.switchShowWidget.setOnCheckedChangeListener { _, c -> settings.setShowWidgetSlot(c) }
        binding.switchShowSearch.isChecked = settings.showSearchBar.value == true
        binding.switchShowSearch.setOnCheckedChangeListener { _, c -> settings.setShowSearchBar(c) }
        binding.switchHaptic.isChecked = settings.hapticEnabled.value == true
        binding.switchHaptic.setOnCheckedChangeListener { _, c -> settings.setHapticEnabled(c) }

        binding.itemResetLayout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset)
                .setMessage(R.string.settings_reset_sub)
                .setPositiveButton(android.R.string.ok) { _, _ -> settings.resetHomeLayout(); finish() }
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
                    data = android.net.Uri.parse("package:$packageName")
                }
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
}
