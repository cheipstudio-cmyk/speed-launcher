package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        // Griglia (radio)
        when {
            settings.gridCols.value == 4 && settings.gridRows.value == 4 -> binding.gridRadio4x4.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 5 -> binding.gridRadio5x5.isChecked = true
            settings.gridCols.value == 5 && settings.gridRows.value == 6 -> binding.gridRadio5x6.isChecked = true
        }
        binding.gridRadioGroup.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.gridRadio4x4 -> settings.setGrid(4, 4)
                R.id.gridRadio5x5 -> settings.setGrid(5, 5)
                R.id.gridRadio5x6 -> settings.setGrid(5, 6)
            }
        }

        // Toggle widget
        binding.switchShowWidget.isChecked = settings.showWidgetSlot.value == true
        binding.switchShowWidget.setOnCheckedChangeListener { _, checked ->
            settings.setShowWidgetSlot(checked)
        }

        // Toggle search bar
        binding.switchShowSearch.isChecked = settings.showSearchBar.value == true
        binding.switchShowSearch.setOnCheckedChangeListener { _, checked ->
            settings.setShowSearchBar(checked)
        }

        // Toggle haptic
        binding.switchHaptic.isChecked = settings.hapticEnabled.value == true
        binding.switchHaptic.setOnCheckedChangeListener { _, checked ->
            settings.setHapticEnabled(checked)
        }

        // Reset layout
        binding.itemResetLayout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset)
                .setMessage(R.string.settings_reset_sub)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    settings.resetHomeLayout()
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Mostra tutorial
        binding.itemShowTutorial.setOnClickListener {
            settings.resetTutorial()
            finish()
        }

        // System
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
}
