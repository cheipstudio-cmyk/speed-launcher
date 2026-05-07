package org.cheipstudio.speedlauncher.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp

/**
 * v30: tracker degli usi delle app per la sezione "Raccomandate" (AI Launcher Mode).
 *
 * Strategia:
 * - Per ogni app key memorizziamo: count totale, lastOpened (ms), recentTimestamps (ultimi 20).
 * - Il punteggio finale è: count * decay(now - lastOpened)
 *   dove decay = exp(-deltaDays / HALF_LIFE_DAYS), con half-life di 7 giorni.
 * - Le app aperte 1 settimana fa pesano la metà di quelle aperte adesso.
 * - Le app aperte 1 mese fa pesano ~1/16.
 *
 * Persistenza: SharedPreferences "speed_usage" con key "data" → JSON.
 */
class AppUsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences("speed_usage", Context.MODE_PRIVATE)
    private val data: MutableMap<String, AppUsage> = mutableMapOf()

    init {
        load()
    }

    private fun load() {
        val raw = prefs.getString(KEY_DATA, null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val node = root.optJSONObject(k) ?: continue
                data[k] = AppUsage(
                    count = node.optInt("count", 0),
                    lastOpened = node.optLong("lastOpened", 0L)
                )
            }
        } catch (_: Throwable) {}
    }

    private fun persist() {
        val root = JSONObject()
        for ((k, v) in data) {
            val node = JSONObject()
            node.put("count", v.count)
            node.put("lastOpened", v.lastOpened)
            root.put(k, node)
        }
        prefs.edit().putString(KEY_DATA, root.toString()).apply()
    }

    /**
     * Chiamato quando un'app viene lanciata.
     */
    fun recordLaunch(appKey: String) {
        val current = data[appKey] ?: AppUsage(0, 0L)
        data[appKey] = AppUsage(
            count = current.count + 1,
            lastOpened = System.currentTimeMillis()
        )
        persist()
    }

    /**
     * Restituisce le top N app per score (frequenza + recency con decay).
     * @param availableKeys solo queste app sono considerate (per filtrare app disinstallate / hidden)
     * @param topN numero di risultati (5 di default)
     */
    fun getTopApps(availableKeys: Set<String>, topN: Int = 5): List<String> {
        val now = System.currentTimeMillis()
        return data
            .filter { availableKeys.contains(it.key) }
            .map { (key, usage) -> key to score(usage, now) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Score con decay esponenziale (half-life 7 giorni).
     */
    private fun score(usage: AppUsage, now: Long): Double {
        if (usage.count == 0 || usage.lastOpened == 0L) return 0.0
        val deltaMs = now - usage.lastOpened
        val deltaDays = deltaMs / (1000.0 * 60 * 60 * 24)
        // exp(-deltaDays * ln(2) / HALF_LIFE)
        val decay = exp(-deltaDays * 0.6931 / HALF_LIFE_DAYS)
        return usage.count * decay
    }

    fun clear() {
        data.clear()
        prefs.edit().clear().apply()
    }

    private data class AppUsage(val count: Int, val lastOpened: Long)

    companion object {
        private const val KEY_DATA = "data"
        private const val HALF_LIFE_DAYS = 7.0
    }
}
