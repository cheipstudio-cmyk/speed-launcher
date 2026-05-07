package org.cheipstudio.speedlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v16: persistenza con supporto folder.
 * Schema JSON: { type, key, page, cellX, cellY, name?, folderApps? }
 */
class HomeLayoutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("speed_home_layout", Context.MODE_PRIVATE)

    fun load(): List<HomeItem> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<HomeItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val folderApps = if (o.has("folderApps")) {
                    val fa = o.getJSONArray("folderApps")
                    (0 until fa.length()).map { fa.getString(it) }
                } else emptyList()
                out.add(HomeItem(
                    key = o.getString("key"),
                    page = o.optInt("page", 0),
                    cellX = o.getInt("cellX"),
                    cellY = o.getInt("cellY"),
                    type = o.optString("type", HomeItem.TYPE_APP),
                    name = o.optString("name", ""),
                    folderApps = folderApps
                ))
            }
            out
        } catch (_: Throwable) { emptyList() }
    }

    fun loadPage(page: Int): List<HomeItem> = load().filter { it.page == page }

    fun savePage(page: Int, pageItems: List<HomeItem>) {
        val others = load().filter { it.page != page }
        save(others + pageItems)
    }

    fun save(items: List<HomeItem>) {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("key", it.key)
            o.put("page", it.page)
            o.put("cellX", it.cellX)
            o.put("cellY", it.cellY)
            o.put("type", it.type)
            if (it.name.isNotEmpty()) o.put("name", it.name)
            if (it.folderApps.isNotEmpty()) {
                val fa = JSONArray()
                for (k in it.folderApps) fa.put(k)
                o.put("folderApps", fa)
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    companion object { private const val KEY_ITEMS = "items" }
}
