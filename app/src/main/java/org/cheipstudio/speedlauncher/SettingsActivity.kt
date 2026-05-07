package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AppCompatActivity
import org.cheipstudio.speedlauncher.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) { "1.0" }
        binding.versionValue.text = getString(R.string.version_value, versionName)

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
