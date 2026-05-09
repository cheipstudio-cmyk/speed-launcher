package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.cheipstudio.speedlauncher.databinding.ActivitySettingsIndexBinding

/**
 * v151: schermata principale Settings stile Niagara/Pixel.
 * Mostra solo categorie. Ogni voce apre SettingsActivity con extra "filterSection".
 */
class SettingsIndexActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsIndexBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)
        window.navigationBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)

        binding = ActivitySettingsIndexBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.idxAspetto.setOnClickListener { openSection("appearance") }
        binding.idxHome.setOnClickListener { openSection("home") }
        binding.idxDrawer.setOnClickListener { openSection("drawer") }
        binding.idxSearch.setOnClickListener { openSection("search") }
        binding.idxGestures.setOnClickListener { openSection("gestures") }
        binding.idxLanguage.setOnClickListener { openSection("language") }
        binding.idxBackup.setOnClickListener { openSection("backup") }
        binding.idxAdvanced.setOnClickListener { openSection("") }
        binding.idxInfo.setOnClickListener { openSection("info") }
    }

    private fun openSection(section: String) {
        val intent = Intent(this, SettingsActivity::class.java)
        if (section.isNotEmpty()) intent.putExtra("filterSection", section)
        startActivity(intent)
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out)
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
