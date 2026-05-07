package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
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

        // ---- Setup griglia (radio) ----
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

        // ---- Toggle dock e widget ----
        binding.switchShowDock.isChecked = settings.showDock.value == true
        binding.switchShowDock.setOnCheckedChangeListener { _, checked ->
            settings.setShowDock(checked)
        }
        binding.switchShowWidget.isChecked = settings.showWidgetSlot.value == true
        binding.switchShowWidget.setOnCheckedChangeListener { _, checked ->
            settings.setShowWidgetSlot(checked)
        }

        // ---- Voci di sistema ----
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
