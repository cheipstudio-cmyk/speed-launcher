package org.cheipstudio.speedlauncher

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
import org.cheipstudio.speedlauncher.data.AppRepository
import org.cheipstudio.speedlauncher.data.AppUsageTracker
import org.cheipstudio.speedlauncher.data.SettingsRepository
import org.cheipstudio.speedlauncher.notifications.NotificationCounter

/**
 * Application class — punto centrale per le singleton repositories.
 * v102: ricreato dopo perdita del file.
 */
class SpeedApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var notificationCounter: NotificationCounter
        private set
    lateinit var usageTracker: AppUsageTracker
        private set

    /** v88: handler globale per drag & drop fra pagine home / drawer */
    var dragHandler: ((String, String, String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        // v175: log crash globale (utile per investigare crash a freddo dopo reset)
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                android.util.Log.e("SpeedApp", "FATAL on ${thread.name}", ex)
                val sw = java.io.StringWriter()
                ex.printStackTrace(java.io.PrintWriter(sw))
                val crashText = sw.toString().take(8000)
                getSharedPreferences("speed_crash", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", crashText)
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .commit()
                // v211: salvo anche su file accessibile da file manager
                try {
                    val dir = getExternalFilesDir(null)
                    if (dir != null) {
                        val f = java.io.File(dir, "last_crash.txt")
                        f.writeText(
                            "Time: ${java.util.Date()}\n" +
                            "Thread: ${thread.name}\n" +
                            "Message: ${ex.message}\n\n" +
                            crashText
                        )
                    }
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
            try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
        }
        instance = this
        // v113: Material You — accent color del sistema applicato a tutte le activities
        DynamicColors.applyToActivitiesIfAvailable(this)
        settingsRepository = SettingsRepository(this)
        appRepository = AppRepository(this)
        notificationCounter = NotificationCounter()
        usageTracker = AppUsageTracker(this)
        // v88: avvia osservazione package changes (install/uninstall)
        appRepository.observePackageChanges()
    }

    companion object {
        lateinit var instance: SpeedApp
            private set
    }
}
