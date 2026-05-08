package org.cheipstudio.speedlauncher.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v80: controlla GitHub Releases per nuovi update di Speed Launcher.
 * Confronta versionName con il tag dell'ultima release.
 * Se c'è una nuova versione, ritorna i dettagli; altrimenti null.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API_URL =
        "https://api.github.com/repos/cheipstudio-cmyk/speed-launcher/releases/latest"

    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String?,
        val releaseNotes: String,
        val isUpdateAvailable: Boolean
    )

    /**
     * Check async. Esegue su thread bg, callback su main thread.
     * @param onResult chiamato con UpdateInfo se la chiamata HTTP riesce; null se errore.
     */
    fun checkForUpdate(context: Context, onResult: (UpdateInfo?) -> Unit) {
        Thread {
            val result = try {
                val url = URL(RELEASES_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    parseRelease(context, response)
                } else {
                    Log.w(TAG, "HTTP $code from GitHub API")
                    null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Update check failed", e)
                null
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.start()
    }

    private fun parseRelease(context: Context, json: String): UpdateInfo? {
        return try {
            val obj = JSONObject(json)
            val tagName = obj.optString("tag_name", "").removePrefix("v")
            val name = obj.optString("name", "")
            val body = obj.optString("body", "")
            val assets = obj.optJSONArray("assets")
            var downloadUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            val currentVersion = try {
                context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Throwable) { "" }

            val isAvailable = compareVersions(tagName, currentVersion) > 0

            UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = tagName,
                downloadUrl = downloadUrl,
                releaseNotes = body.ifBlank { name },
                isUpdateAvailable = isAvailable
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to parse release", e)
            null
        }
    }

    /**
     * Compare semver-style versions. Returns positive if a > b, 0 if equal, negative if a < b.
     * Handles "0.80.0" vs "0.79.5" correctly.
     */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    /** Apre il browser sull'URL dell'APK */
    fun openDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {}
    }
}
