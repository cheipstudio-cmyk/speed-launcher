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
    private val ownPackage = context.packageName

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
        val users: List<UserHandle> = listOf(Process.myUserHandle())
        for (user in users) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (t: Throwable) {
                emptyList()
            }
            for (activity in activities) {
                val pkg = activity.applicationInfo.packageName
                if (pkg == ownPackage) continue
                result.add(
                    AppInfo(
                        packageName = pkg,
                        componentName = activity.componentName.className,
                        label = activity.label?.toString() ?: pkg,
                        icon = activity.getBadgedIcon(0),
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
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
        })
    }

    /**
     * v9: animazione di apertura app più leggera — usa l'animazione di sistema
     * di default (è già fluida e nativa). Niente più makeScaleUpAnimation pesante.
     */
    fun launch(app: AppInfo, sourceView: android.view.View? = null) {
        val component = android.content.ComponentName(app.packageName, app.componentName)
        try {
            launcherApps.startMainActivity(component, app.userHandle, null, null)
        } catch (t: Throwable) {
            reload()
        }
    }
}
