package org.cheipstudio.speedlauncher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracker per il conteggio dei lanci di ogni app, usato dalle Raccomandate AI.
 * v102: ricreato dopo perdita del file.
 */
class AppUsageTracker(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("speed_app_usage", Context.MODE_PRIVATE)

    fun recordLaunch(appKey: String) {
        val current = prefs.getInt(appKey, 0)
        prefs.edit().putInt(appKey, current + 1).apply()
        prefs.edit().putLong("${appKey}__last", System.currentTimeMillis()).apply()
    }

    fun getLaunchCount(appKey: String): Int {
        return prefs.getInt(appKey, 0)
    }

    fun getLastLaunchTime(appKey: String): Long {
        return prefs.getLong("${appKey}__last", 0L)
    }

    /** Ritorna le app con conteggio > 0 ordinate per: most recent first, poi più frequenti */
    fun getMostRecentlyUsed(): List<String> {
        val all = prefs.all
        return all.entries
            .filter { it.key.endsWith("__last") && (it.value as? Long ?: 0L) > 0L }
            .map { it.key.removeSuffix("__last") to (it.value as Long) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun getMostUsed(): List<String> {
        val all = prefs.all
        return all.entries
            .filter { !it.key.endsWith("__last") && (it.value as? Int ?: 0) > 0 }
            .map { it.key to (it.value as Int) }
            .sortedByDescending { it.second }
            .map { it.first }
    }


    /**
     * v112: ritorna i topN apps tra le keys disponibili, ordinate per uso più recente.
     * Usato da RecommendedView in modalità AI.
     */
    fun getTopApps(availableKeys: Set<String>, topN: Int): List<String> {
        // Prima prova: most recently used (cronologico)
        val recent = getMostRecentlyUsed().filter { it in availableKeys }
        if (recent.isNotEmpty()) return recent.take(topN)
        // Fallback: most used (frequenza)
        return getMostUsed().filter { it in availableKeys }.take(topN)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
