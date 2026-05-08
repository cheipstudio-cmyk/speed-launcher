package org.cheipstudio.speedlauncher

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * v38: gestisce l'applicazione della lingua scelta dall'utente.
 * Usa AppCompatDelegate.setApplicationLocales che fa il riavvio attivity automatico.
 *
 * code = "auto" → segue il sistema (default)
 * code = "it"|"en"|"fr"|"es"|"tr"|"ar"|"ja" → forza quella lingua
 */
object LanguageHelper {
    val SUPPORTED_LANGUAGES = listOf(
        "auto" to "Auto (sistema)",
        "it" to "Italiano",
        "en" to "English",
        "fr" to "Français",
        "es" to "Español",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "ja" to "日本語"
    )

    fun applyLanguage(code: String) {
        val locales = if (code == "auto" || code.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun displayNameFor(code: String): String {
        return SUPPORTED_LANGUAGES.firstOrNull { it.first == code }?.second ?: "Auto"
    }
}
