package org.cheipstudio.speedlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    
    /** v131: cache icone — evita re-fetch costoso ad ogni reload (getBadgedIcon scaling/badging) */
    private val iconCache = mutableMapOf<String, android.graphics.drawable.Drawable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val apps = MutableLiveData<List<AppInfo>>(emptyList())

    /** v88: callback chiamata quando una nuova app viene installata (dopo reload) */
    var onNewPackageInstalled: ((String) -> Unit)? = null

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { loadApps() }
            apps.postValue(list)
        }
    }

    private fun loadApps(): List<AppInfo> {
        val result = mutableListOf<AppInfo>()
        val users: List<UserHandle> = listOf(Process.myUserHandle())
        // v122: escludo Speed Launcher stesso quando è il default launcher attivo
        // (se l'utente usa un altro launcher, deve poter aprire Speed Launcher dal drawer)
        val ownPkg = context.packageName
        val isDefaultLauncher = isMyselfDefaultLauncher()
        for (user in users) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (t: Throwable) {
                emptyList()
            }
            for (activity in activities) {
                // Skip se è Speed Launcher stesso E sono il launcher di default
                if (isDefaultLauncher && activity.applicationInfo.packageName == ownPkg) continue
                val pkg = activity.applicationInfo.packageName
                val cls = activity.componentName.className
                val cacheKey = "$pkg/$cls"
                // v131: usa cache se presente, altrimenti decodifica e cacha
                val icon = iconCache[cacheKey] ?: activity.getBadgedIcon(0).also {
                    iconCache[cacheKey] = it
                }
                result.add(
                    AppInfo(
                        packageName = pkg,
                        componentName = cls,
                        label = activity.label?.toString() ?: pkg,
                        icon = icon,
                        userHandle = user
                    )
                )
            }
        }
        val collator = Collator.getInstance()
        return result.sortedWith(compareBy(collator) { it.label.lowercase() })
    }

    fun observePackageChanges() {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                // v88: notifica listener della nuova app, poi reload
                reloadAndNotify(packageName)
            }
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
        })
    }

    /** v131: invalida cache icone per un pacchetto (es. package aggiornato/rimosso) */
    private fun invalidateIconCache(packageName: String) {
        iconCache.entries.removeAll { it.key.startsWith("$packageName/") }
    }

    private fun reloadAndNotify(packageName: String) {
        // v131: invalida cache vecchia per il pacchetto cambiato
        invalidateIconCache(packageName)
        scope.launch {
            val list = withContext(Dispatchers.IO) { loadApps() }
            apps.postValue(list)
            // Trovo la nuova app aggiunta (può avere più componentName, prendo il primo)
            val newApp = list.firstOrNull { it.packageName == packageName }
            if (newApp != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onNewPackageInstalled?.invoke(newApp.key)
                }
            }
        }
    }

    companion object {
        /** v288: ultimo package lanciato - usato da MainActivity per drop animation al ritorno home */
        @Volatile var lastLaunchedPackage: String? = null
        @Volatile var lastLaunchOriginX: Float = 0f
        @Volatile var lastLaunchOriginY: Float = 0f
        @Volatile var lastLaunchTimestamp: Long = 0L
        /** v292: pagina home da cui l'app è stata aperta - per ritornare alla stessa pagina */
        @Volatile var lastLaunchPageIndex: Int = -1
        /** v292: era dentro una folder? folder uuid */
        @Volatile var lastLaunchFolderUuid: String? = null
    }
    
    fun launch(app: AppInfo, sourceView: android.view.View? = null) {
        // v288: registro l'origine per la drop animation al ritorno home
        try {
            lastLaunchedPackage = app.packageName
            lastLaunchTimestamp = System.currentTimeMillis()
            if (sourceView != null && sourceView.width > 0) {
                val loc = IntArray(2)
                sourceView.getLocationOnScreen(loc)
                lastLaunchOriginX = (loc[0] + sourceView.width / 2f)
                lastLaunchOriginY = (loc[1] + sourceView.height / 2f)
            } else {
                lastLaunchOriginX = 0f
                lastLaunchOriginY = 0f
            }
            // v292: registro la pagina home da cui parte l'app (se proviene dalla home grid)
            lastLaunchPageIndex = -1
            lastLaunchFolderUuid = null
            try {
                var v: android.view.View? = sourceView
                while (v != null) {
                    if (v is org.cheipstudio.speedlauncher.ui.IconGridView) {
                        lastLaunchPageIndex = v.pageIndex
                        break
                    }
                    v = v.parent as? android.view.View
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        // v292: zoom-out launch (opposto della drop) - l'icona "esplode" verso lo schermo dell'app
        if (sourceView != null && sourceView.width > 0) {
            try {
                sourceView.animate().cancel()
                // Subito piccolo squash percettivo
                sourceView.animate()
                    .scaleX(0.92f).scaleY(0.92f)
                    .setDuration(50)
                    .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
                    .withEndAction {
                        try {
                            // Zoom out crescente verso lo schermo  
                            sourceView.animate()
                                .scaleX(1.6f).scaleY(1.6f)
                                .alpha(0.3f)
                                .setDuration(120)
                                .setInterpolator(android.view.animation.AccelerateInterpolator(2.0f))
                                .start()
                            doLaunch(app, sourceView)
                            // Reset al ritorno (dopo che onResume avrà tempo di prendere il controllo)
                            sourceView.postDelayed({
                                try {
                                    sourceView.scaleX = 1f; sourceView.scaleY = 1f; sourceView.alpha = 1f
                                } catch (_: Throwable) {}
                            }, 800L)
                        } catch (_: Throwable) {
                            doLaunch(app, sourceView)
                        }
                    }
                    .start()
                return
            } catch (_: Throwable) {}
        }
        doLaunch(app, sourceView)
    }
    
    private fun doLaunch(app: AppInfo, sourceView: android.view.View?) {
        val component = android.content.ComponentName(app.packageName, app.componentName)
        val (sourceBounds, options) = buildLaunchAnimation(sourceView)
        try {
            launcherApps.startMainActivity(component, app.userHandle, sourceBounds, options?.toBundle())
            try {
                org.cheipstudio.speedlauncher.SpeedApp.instance.usageTracker.recordLaunch(app.key)
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            reload()
        }
    }

    /**
     * v82: animazione di apertura "Pixel-style".
     */
    private fun buildLaunchAnimation(
        view: android.view.View?
    ): Pair<android.graphics.Rect?, android.app.ActivityOptions?> {
        if (view == null || view.width == 0 || view.height == 0) {
            return null to null
        }
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val bounds = android.graphics.Rect(
            loc[0], loc[1],
            loc[0] + view.width, loc[1] + view.height
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val options = android.app.ActivityOptions.makeClipRevealAnimation(
                    view, 0, 0, view.width, view.height
                )
                options.setSplashScreenStyle(
                    android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON
                )
                return bounds to options
            } catch (_: Throwable) {}
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val options = android.app.ActivityOptions.makeClipRevealAnimation(
                    view, view.width / 2, view.height / 2, 0, 0
                )
                return bounds to options
            } catch (_: Throwable) {}
        }

        val options = android.app.ActivityOptions.makeScaleUpAnimation(
            view, 0, 0, view.width, view.height
        )
        return bounds to options
    }

    /** v122: verifica se Speed Launcher è il launcher di default attivo */
    private fun isMyselfDefaultLauncher(): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Throwable) { false }
    }
}
