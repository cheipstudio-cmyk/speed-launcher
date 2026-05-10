package org.cheipstudio.speedlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v240: Persistenza widget per pagina (JSON in SharedPreferences).
 *
 * Schema:
 * "page_<idx>" → JSON array di WidgetItem
 *
 * Gestisce migrazione automatica dal vecchio sistema "lastWidgetId" (single widget)
 * alla nuova lista per pagina.
 */
class WidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    /** Carica tutti i widget di una pagina specifica */
    fun loadPage(pageIndex: Int): List<WidgetItem> {
        val raw = prefs.getString("page_$pageIndex", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<WidgetItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(WidgetItem(
                    uuid = obj.optString("uuid", ""),
                    appWidgetId = obj.optInt("appWidgetId", -1),
                    pageIndex = obj.optInt("pageIndex", pageIndex),
                    cellX = obj.optInt("cellX", 0),
                    cellY = obj.optInt("cellY", 0),
                    spanX = obj.optInt("spanX", 4),
                    spanY = obj.optInt("spanY", 2),
                    verticalPos = obj.optString("verticalPos", WidgetItem.POS_TOP)
                ))
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Salva tutti i widget di una pagina (sostituisce esistenti) */
    fun savePage(pageIndex: Int, widgets: List<WidgetItem>) {
        val arr = JSONArray()
        for (w in widgets) {
            val obj = JSONObject()
            obj.put("uuid", w.uuid)
            obj.put("appWidgetId", w.appWidgetId)
            obj.put("pageIndex", w.pageIndex)
            obj.put("cellX", w.cellX)
            obj.put("cellY", w.cellY)
            obj.put("spanX", w.spanX)
            obj.put("spanY", w.spanY)
            obj.put("verticalPos", w.verticalPos)
            arr.put(obj)
        }
        prefs.edit().putString("page_$pageIndex", arr.toString()).apply()
    }

    /** Aggiunge un widget a una pagina */
    fun addWidget(item: WidgetItem) {
        val current = loadPage(item.pageIndex).toMutableList()
        // Rimuove eventuali duplicati con stesso uuid o appWidgetId
        current.removeAll { it.uuid == item.uuid || it.appWidgetId == item.appWidgetId }
        current.add(item)
        savePage(item.pageIndex, current)
    }

    /** Rimuove un widget per uuid */
    fun removeWidget(pageIndex: Int, uuid: String) {
        val current = loadPage(pageIndex).toMutableList()
        current.removeAll { it.uuid == uuid }
        savePage(pageIndex, current)
    }

    /** Rimuove un widget per appWidgetId (utile quando l'app è disinstallata) */
    fun removeByAppWidgetId(appWidgetId: Int) {
        for (i in 0..MAX_PAGES) {
            val items = loadPage(i)
            if (items.any { it.appWidgetId == appWidgetId }) {
                savePage(i, items.filter { it.appWidgetId != appWidgetId })
            }
        }
    }

    /** Aggiorna un widget esistente (es. dopo resize/move) */
    fun updateWidget(updated: WidgetItem) {
        val current = loadPage(updated.pageIndex).toMutableList()
        val idx = current.indexOfFirst { it.uuid == updated.uuid }
        if (idx >= 0) {
            current[idx] = updated
            savePage(updated.pageIndex, current)
        } else {
            addWidget(updated)
        }
    }

    /** Ritorna tutte le pagine che hanno almeno un widget */
    fun loadAll(): Map<Int, List<WidgetItem>> {
        val out = mutableMapOf<Int, List<WidgetItem>>()
        for (i in 0..MAX_PAGES) {
            val items = loadPage(i)
            if (items.isNotEmpty()) out[i] = items
        }
        return out
    }

    /**
     * Migrazione dal vecchio sistema (lastWidgetId in speed_widget_host).
     * Chiamare una volta al primo boot della v2.4. Se trova un widget singolo
     * lo posiziona in pagina 0 con span pieno.
     */
    fun migrateFromLegacyIfNeeded(context: Context) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacyPrefs = context.getSharedPreferences("speed_widget_host", Context.MODE_PRIVATE)
        val legacyId = legacyPrefs.getInt("last_widget_id", -1)
        if (legacyId >= 0) {
            // Crea WidgetItem in pagina 0 che occupa tutta la griglia widget (4x2)
            val migrated = WidgetItem(
                uuid = "migrated_${legacyId}_${System.currentTimeMillis()}",
                appWidgetId = legacyId,
                pageIndex = 0,
                cellX = 0, cellY = 0,
                spanX = WidgetItem.GRID_COLS,
                spanY = 2
            )
            addWidget(migrated)
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    companion object {
        private const val STORE_NAME = "speed_widget_store_v240"
        private const val KEY_MIGRATED = "migrated_from_legacy"
        private const val MAX_PAGES = 32
    }
}
