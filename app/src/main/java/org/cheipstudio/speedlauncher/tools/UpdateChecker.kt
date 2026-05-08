package org.cheipstudio.speedlauncher.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * v80-83: controlla GitHub Releases per nuovi update.
 * v83: scarica e apre installer di sistema automaticamente.
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
        val isUpdateAvailable: Boolean,
        val errorReason: String? = null
    )

    /**
     * Errori che possono uscire:
     * - "no_release": nessuna release pubblicata su GitHub (404)
     * - "no_internet": rete non raggiungibile
     * - "parse_error": JSON malformato
     * - null: tutto ok
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
                when (code) {
                    200 -> {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        parseRelease(context, response)
                    }
                    404 -> {
                        Log.w(TAG, "No release published yet (404)")
                        UpdateInfo("", "", null, "", false, "no_release")
                    }
                    else -> {
                        Log.w(TAG, "HTTP $code from GitHub API")
                        UpdateInfo("", "", null, "", false, "http_$code")
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                UpdateInfo("", "", null, "", false, "no_internet")
            } catch (e: Throwable) {
                Log.w(TAG, "Update check failed", e)
                UpdateInfo("", "", null, "", false, "parse_error")
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

    /**
     * v83: scarica l'APK e apre l'installer di sistema.
     * @param onProgress chiamato con (downloaded, total) durante il download. total=-1 se sconosciuto.
     * @param onComplete chiamato con il File scaricato (o null se errore).
     */
    fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Long, Long) -> Unit,
        onComplete: (File?) -> Unit
    ) {
        Thread {
            val result = try {
                val downloadsDir = File(context.getExternalFilesDir(null), "updates")
                downloadsDir.mkdirs()
                // Pulisco vecchi APK
                downloadsDir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
                val outFile = File(downloadsDir, "speed_launcher_update.apk")

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            Handler(Looper.getMainLooper()).post {
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                outFile
            } catch (e: Throwable) {
                Log.w(TAG, "Download failed", e)
                null
            }
            Handler(Looper.getMainLooper()).post {
                onComplete(result)
                if (result != null) {
                    openInstaller(context, result)
                }
            }
        }.start()
    }

    /**
     * Apre l'installer Android di sistema con l'APK scaricato.
     * L'utente vedrà la classica schermata "Vuoi installare questa app?" e dovrà tappare "Installa".
     */
    private fun openInstaller(context: Context, apkFile: File) {
        try {
            val authority = context.packageName + ".fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to open installer", e)
        }
    }

    /** Vecchio: apre il browser sull'URL. Tenuto come fallback. */
    fun openDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {}
    }
}
