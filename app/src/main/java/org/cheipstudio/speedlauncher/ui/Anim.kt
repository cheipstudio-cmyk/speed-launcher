package org.cheipstudio.speedlauncher.ui

import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat

object Anim {
    /**
     * Bounce overshoot stile Pixel quando un'icona riceve una notifica.
     * Veloce, leggermente over-spring, useNativeDriver-equivalent (compose native via ViewCompat).
     */
    fun bounceIn(view: View) {
        view.scaleX = 0.7f
        view.scaleY = 0.7f
        ViewCompat.animate(view)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(2.5f))
            .setDuration(280)
            .start()
    }

    /**
     * Tap-feedback sull'icona (press in + release).
     */
    fun pressFeedback(view: View) {
        ViewCompat.animate(view)
            .scaleX(0.92f).scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                ViewCompat.animate(view)
                    .scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2f))
                    .setDuration(180)
                    .start()
            }
            .start()
    }
}
