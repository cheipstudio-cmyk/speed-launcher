package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v220: helper centralizzato per haptic feedback.
 * Rispetta il setting `hapticEnabled` su TUTTI i punti dell'app.
 */
object HapticHelper {
    fun feedback(view: View?, constant: Int) {
        if (SpeedApp.instance.settingsRepository.hapticEnabled.value != true) return
        try {
            if (view != null) {
                view.performHapticFeedback(constant)
            } else {
                fallback()
            }
        } catch (_: Throwable) {
            try { fallback() } catch (_: Throwable) {}
        }
    }
    
    fun longPress(view: View?) = feedback(view, HapticFeedbackConstants.LONG_PRESS)
    fun contextClick(view: View?) = feedback(view, HapticFeedbackConstants.CONTEXT_CLICK)
    fun click(view: View?) = feedback(view, HapticFeedbackConstants.VIRTUAL_KEY)
    fun tick(view: View?) = feedback(view, HapticFeedbackConstants.CLOCK_TICK)
    fun gestureStart(view: View?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            feedback(view, HapticFeedbackConstants.GESTURE_START)
        } else {
            feedback(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    
    private fun fallback() {
        if (SpeedApp.instance.settingsRepository.hapticEnabled.value != true) return
        try {
            val ctx: Context = SpeedApp.instance
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createOneShot(15L, 80))
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                v?.vibrate(android.os.VibrationEffect.createOneShot(15L, 80))
            }
        } catch (_: Throwable) {}
    }
}
