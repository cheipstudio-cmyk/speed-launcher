package org.cheipstudio.speedlauncher

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.cheipstudio.speedlauncher.data.AppRepository
import org.cheipstudio.speedlauncher.notifications.NotificationCounter

class SpeedApp : Application() {

    val appRepository: AppRepository by lazy { AppRepository(this) }
    val notificationCounter: NotificationCounter by lazy { NotificationCounter() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DynamicColors.applyToActivitiesIfAvailable(this)
        appRepository.observePackageChanges()
    }

    companion object {
        lateinit var instance: SpeedApp
            private set
    }
}
