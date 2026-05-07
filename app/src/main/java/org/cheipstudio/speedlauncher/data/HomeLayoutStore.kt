package org.cheipstudio.speedlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HomeLayoutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("speed_home_layout", Context.MODE_PRIVATE)

    fun load(): List<HomeItem> {
        val raw = prefs.getString(KEY_GRID, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<HomeItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                HomeItem(
                    key = o.getString("key"),
                    page = o.optInt("page", 0),
                    cellX = o.getInt("cellX"),
                    cellY = o.getInt("cellY")
                )
            )
        }
        return out
    }

    /** Carica solo gli item della pagina specificata */
    fun loadPage(page: Int): List<HomeItem> {
        return load().filter { it.page == page }
    }

    fun save(items: List<HomeItem>) {
        val arr = JSONArray()
        for (it in items) {
            arr.put(JSONObject().apply {
                put("key", it.key)
                put("page", it.page)
                put("cellX", it.cellX)
                put("cellY", it.cellY)
            })
        }
        prefs.edit().putString(KEY_GRID, arr.toString()).apply()
    }

    /** Salva sostituendo solo gli item di una specifica pagina */
    fun savePage(page: Int, items: List<HomeItem>) {
        val others = load().filter { it.page != page }
        save(others + items)
    }

    fun loadDock(): List<String> {
        val raw = prefs.getString(KEY_DOCK, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun saveDock(keys: List<String>) {
        val arr = JSONArray()
        for (k in keys) arr.put(k)
        prefs.edit().putString(KEY_DOCK, arr.toString()).apply()
    }

    companion object {
        private const val KEY_GRID = "items"
        private const val KEY_DOCK = "dock"
    }
}

data class HomeItem(
    val key: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int
)
