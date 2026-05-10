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
        // v203: fade-in immediato per percezione di apertura veloce
        binding.root.alpha = 0f
        binding.root.animate()
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // v202: tap effect istantaneo + apertura sezione
        bindCardTap(binding.idxAspetto) { openSection("appearance") }
        bindCardTap(binding.idxHome) { openSection("home") }
        bindCardTap(binding.idxAi) { openSection("ai") }
        bindCardTap(binding.idxDrawer) { openSection("drawer") }
        bindCardTap(binding.idxSearch) { openSection("search") }
        bindCardTap(binding.idxGestures) { openSection("gestures") }
        bindCardTap(binding.idxLanguage) { openSection("language") }
        bindCardTap(binding.idxBackup) { openSection("backup") }
        bindCardTap(binding.idxSystem) { openSection("system") }
        bindCardTap(binding.idxInfo) { openSection("info") }
    }
    
    private fun bindCardTap(card: android.view.View, action: () -> Unit) {
        card.setOnClickListener {
            // Push effect: scale 1 → 0.96 → 1 mentre starta l\'activity
            card.animate().cancel()
            card.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(60)
                .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 1f, 0.4f))
                .withEndAction {
                    card.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
                        .start()
                    action()
                }
                .start()
        }
    }

    private fun openSection(section: String) {
        val intent = Intent(this, SettingsActivity::class.java)
        if (section.isNotEmpty()) intent.putExtra("filterSection", section)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            overridePendingTransition(R.anim.slide_in_left_back, R.anim.slide_out_right)
        }
    }
}
