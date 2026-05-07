package org.cheipstudio.speedlauncher

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * v27: backup/restore di tutte le SharedPreferences di Speed Launcher.
 * Esporta e importa un file JSON con: speed_settings + speed_home_layout.
 */
object BackupManager {

    private const val FORMAT_VERSION = 1
    private const val SETTINGS_PREFS = "speed_settings"
    private const val LAYOUT_PREFS = "speed_home_layout"

    /**
     * Esporta tutto in un JSONObject che il chiamante può scrivere su un Uri.
     */
    fun exportToJson(context: Context): String {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("app", "speed-launcher")
        root.put("exportedAt", System.currentTimeMillis())

        root.put("settings", prefsToJson(context, SETTINGS_PREFS))
        root.put("layout", prefsToJson(context, LAYOUT_PREFS))

        return root.toString(2)
    }

    private fun prefsToJson(context: Context, name: String): JSONObject {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val obj = JSONObject()
        for ((k, v) in prefs.all) {
            when (v) {
                null -> {}
                is String -> obj.put(k, JSONObject().apply { put("type", "string"); put("value", v) })
                is Int -> obj.put(k, JSONObject().apply { put("type", "int"); put("value", v) })
                is Long -> obj.put(k, JSONObject().apply { put("type", "long"); put("value", v) })
                is Float -> obj.put(k, JSONObject().apply { put("type", "float"); put("value", v.toDouble()) })
                is Boolean -> obj.put(k, JSONObject().apply { put("type", "bool"); put("value", v) })
                is Set<*> -> {
                    val arr = org.json.JSONArray()
                    v.forEach { if (it is String) arr.put(it) }
                    obj.put(k, JSONObject().apply { put("type", "stringSet"); put("value", arr) })
                }
            }
        }
        return obj
    }

    /**
     * Importa un JSON precedentemente esportato. Rimpiazza tutte le prefs correnti.
     */
    fun importFromJson(context: Context, json: String): Result<Unit> {
        return try {
            val root = JSONObject(json)
            val format = root.optInt("format", 0)
            if (format != FORMAT_VERSION) {
                return Result.failure(Exception("Versione file non supportata: $format"))
            }
            if (root.optString("app") != "speed-launcher") {
                return Result.failure(Exception("File non valido per Speed Launcher"))
            }
            val settings = root.optJSONObject("settings")
            val layout = root.optJSONObject("layout")
            if (settings != null) jsonToPrefs(context, SETTINGS_PREFS, settings)
            if (layout != null) jsonToPrefs(context, LAYOUT_PREFS, layout)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun jsonToPrefs(context: Context, name: String, obj: JSONObject) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val node = obj.optJSONObject(key) ?: continue
            val type = node.optString("type")
            when (type) {
                "string" -> editor.putString(key, node.optString("value"))
                "int" -> editor.putInt(key, node.optInt("value"))
                "long" -> editor.putLong(key, node.optLong("value"))
                "float" -> editor.putFloat(key, node.optDouble("value").toFloat())
                "bool" -> editor.putBoolean(key, node.optBoolean("value"))
                "stringSet" -> {
                    val arr = node.optJSONArray("value") ?: continue
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Throwable) { false }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Throwable) { null }
    }

    fun suggestedFilename(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
        return "speed-launcher-backup-${sdf.format(java.util.Date())}.json"
    }
}
