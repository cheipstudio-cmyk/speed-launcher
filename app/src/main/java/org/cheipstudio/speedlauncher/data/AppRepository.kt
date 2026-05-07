package org.cheipstudio.speedlauncher.data

import android.app.ActivityOptions
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
import org.cheipstudio.speedlauncher.R
import java.text.Collator

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ownPackage = context.packageName

    val apps = MutableLiveData<List<AppInfo>>(emptyList())

    init { reload() }

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
            val activities = try { launcherApps.getActivityList(null, user) } catch (_: Throwable) { emptyList() }
            for (activity in activities) {
                val pkg = activity.applicationInfo.packageName
                if (pkg == ownPackage) continue
                result.add(AppInfo(
                    packageName = pkg,
                    componentName = activity.componentName.className,
                    label = activity.label?.toString() ?: pkg,
                    icon = activity.getBadgedIcon(0),
                    userHandle = user
                ))
            }
        }
        val collator = Collator.getInstance()
        return result.sortedWith(compareBy(collator) { it.label.lowercase() })
    }

    fun observePackageChanges() {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String, u: UserHandle) = reload()
            override fun onPackageAdded(p: String, u: UserHandle) = reload()
            override fun onPackageChanged(p: String, u: UserHandle) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle, r: Boolean) = reload()
        })
    }

    /**
     * v14: animazione fade leggero (180ms) tramite ActivityOptions.makeCustomAnimation.
     */
    fun launch(app: AppInfo, sourceView: android.view.View? = null) {
        val component = android.content.ComponentName(app.packageName, app.componentName)
        val options = try {
            ActivityOptions.makeCustomAnimation(context, R.anim.fade_in_fast, R.anim.fade_out_fast)
        } catch (_: Throwable) { null }
        try {
            launcherApps.startMainActivity(component, app.userHandle, null, options?.toBundle())
        } catch (t: Throwable) {
            reload()
        }
    }
}
