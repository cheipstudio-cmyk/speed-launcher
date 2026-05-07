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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val apps = MutableLiveData<List<AppInfo>>(emptyList())

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
        // Per ora carichiamo solo lo user principale; multi-user/work profile arrivano dopo
        val users: List<UserHandle> = listOf(Process.myUserHandle())
        for (user in users) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (t: Throwable) {
                emptyList()
            }
            for (activity in activities) {
                result.add(
                    AppInfo(
                        packageName = activity.applicationInfo.packageName,
                        componentName = activity.componentName.className,
                        label = activity.label?.toString() ?: activity.applicationInfo.packageName,
                        icon = activity.getBadgedIcon(0),
                        userHandle = user
                    )
                )
            }
        }
        val collator = Collator.getInstance()
        return result.sortedWith(compareBy(collator) { it.label.lowercase() })
    }

    /**
     * Si registra ai cambi di package per ricaricare automaticamente.
     */
    fun observePackageChanges() {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
        })
    }

    fun launch(app: AppInfo, sourceView: android.view.View? = null) {
        val component = android.content.ComponentName(app.packageName, app.componentName)
        val (sourceBounds, options) = buildLaunchAnimation(sourceView)
        try {
            launcherApps.startMainActivity(component, app.userHandle, sourceBounds, options?.toBundle())
            // v30: registra il lancio per le Raccomandate (AI Launcher Mode)
            try {
                org.cheipstudio.speedlauncher.SpeedApp.instance.usageTracker.recordLaunch(app.key)
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            // App rimossa o non più avviabile: ricarica
            reload()
        }
    }

    /**
     * Costruisce l'animazione di apertura "scale-up dall'icona" stile Pixel.
     * Restituisce sia i bounds (per sourceBounds di startMainActivity) che le ActivityOptions.
     * Se la view è null o non visibile, animazione di default.
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
        val options = android.app.ActivityOptions.makeScaleUpAnimation(
            view, 0, 0, view.width, view.height
        )
        return bounds to options
    }
}
