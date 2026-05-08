package org.cheipstudio.speedlauncher.tools

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * v75: supporto icon pack esterni (ADW/Apex/Nova compatibility).
 * 
 * Icon pack = APK separato che contiene un file `appfilter.xml` in res/xml/
 * con mapping component → drawable. Esempio:
 *   <item component="ComponentInfo{com.whatsapp/com.whatsapp.HomeActivity}" drawable="whatsapp" />
 *
 * Onestà tecnica:
 * - Funziona con la maggior parte degli icon pack ADW-compatible (Nova, Apex, ecc).
 * - NON supporta dynamic icon adaptive (Pixel-style monochrome) — solo drawable statici.
 * - Performance: carica tutto l'appfilter in memoria. OK per icon pack normali (~1500 icone).
 */
class IconPackManager(private val context: Context) {

    private var packageName: String? = null
    private var resources: Resources? = null
    private val componentToDrawableName = mutableMapOf<String, String>()
    private var loaded = false

    /** Carica icon pack. Ritorna true se OK, false se fallisce. */
    fun load(packPackage: String): Boolean {
        if (packPackage.isEmpty()) return false
        try {
            resources = context.packageManager.getResourcesForApplication(packPackage)
            packageName = packPackage
            componentToDrawableName.clear()
            loadAppFilter()
            loaded = true
            return true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load icon pack: $packPackage", e)
            loaded = false
            return false
        }
    }

    /** Ritorna drawable override per il component (package/activity). null se non disponibile. */
    fun getIconForComponent(packageName: String, activityName: String): Drawable? {
        if (!loaded) return null
        val res = resources ?: return null
        val component1 = "ComponentInfo{$packageName/$activityName}"
        val drawableName = componentToDrawableName[component1] ?: return null
        return try {
            val resId = res.getIdentifier(drawableName, "drawable", this.packageName)
            if (resId == 0) null
            else @Suppress("DEPRECATION") res.getDrawable(resId)
        } catch (_: Throwable) { null }
    }

    /** Ritorna drawable override leggendo dal ComponentName Android. */
    fun getIconForComponent(component: ComponentName): Drawable? =
        getIconForComponent(component.packageName, component.className)

    private fun loadAppFilter() {
        val res = resources ?: return
        val pkg = packageName ?: return
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        if (xmlId == 0) {
            Log.w(TAG, "Icon pack $pkg has no appfilter.xml")
            return
        }
        try {
            val parser = res.getXml(xmlId)
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        componentToDrawableName[component] = drawable
                    }
                }
                event = parser.next()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error parsing appfilter", e)
        }
    }

    /** Lista icon pack installati nel device. */
    companion object {
        private const val TAG = "IconPackManager"

        /** Action filter ADW/Apex/Nova compatibili */
        private val ICON_PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME"
        )

        data class IconPackInfo(val packageName: String, val name: String)

        fun listInstalledIconPacks(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val seen = mutableSetOf<String>()
            val result = mutableListOf<IconPackInfo>()
            for (action in ICON_PACK_ACTIONS) {
                try {
                    val intent = android.content.Intent(action)
                    val pkgs = pm.queryIntentActivities(intent, 0)
                    for (info in pkgs) {
                        val pkg = info.activityInfo.packageName
                        if (pkg in seen) continue
                        seen.add(pkg)
                        val label = try { info.loadLabel(pm).toString() } catch (_: Throwable) { pkg }
                        result.add(IconPackInfo(pkg, label))
                    }
                } catch (_: Throwable) {}
            }
            return result.sortedBy { it.name.lowercase() }
        }
    }
}
