package org.cheipstudio.speedlauncher

import android.app.Application
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
        instance = this
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
