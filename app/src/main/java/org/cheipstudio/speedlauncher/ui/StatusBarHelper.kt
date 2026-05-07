package org.cheipstudio.speedlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build

/**
 * v16: swipe down notifiche con fallback multipli.
 * 1. StatusBarManager.expandNotificationsPanel (Android 4.x+, blocca su alcuni vendor)
 * 2. expandSettingsPanel (Android 4.2+, ma apre quick settings non notifiche)
 * Su Motorola stock il primo dovrebbe funzionare. Se fallisce silenziosamente, è limite OEM.
 */
object StatusBarHelper {

    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context): Boolean {
        return try {
            val sbm = context.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            val method = if (Build.VERSION.SDK_INT >= 17) {
                cls.getMethod("expandNotificationsPanel")
            } else {
                cls.getMethod("expand")
            }
            method.invoke(sbm)
            true
        } catch (_: Throwable) { false }
    }

    @SuppressLint("WrongConstant")
    fun expandQuickSettings(context: Context): Boolean {
        return try {
            val sbm = context.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            val method = cls.getMethod("expandSettingsPanel")
            method.invoke(sbm)
            true
        } catch (_: Throwable) { false }
    }
}
