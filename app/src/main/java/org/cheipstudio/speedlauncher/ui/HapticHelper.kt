package org.cheipstudio.speedlauncher.ui

import android.view.HapticFeedbackConstants
import android.view.View
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v219: helper centralizzato per haptic feedback.
 * Rispetta il setting `hapticEnabled` su TUTTI i punti dell'app.
 */
object HapticHelper {
    fun feedback(view: View?, constant: Int) {
        if (view == null) return
        if (SpeedApp.instance.settingsRepository.hapticEnabled.value != true) return
        try {
            view.performHapticFeedback(constant)
        } catch (_: Throwable) {}
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
}
