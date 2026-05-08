package org.cheipstudio.speedlauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * v31: AccessibilityService usato esclusivamente per spegnere lo schermo via
 * GLOBAL_ACTION_LOCK_SCREEN (API 28+). Questa azione si comporta come la pressione
 * del tasto power: lo schermo si spegne, il sensore biometrico funziona alla riaccensione.
 *
 * NESSUN altro evento di accessibility è osservato/utilizzato — vediamo solo TYPE_VIEW_CLICKED
 * (richiesto per la dichiarazione del service) ma onAccessibilityEvent è no-op.
 */
class SpeedAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op: usiamo questo service solo come "ponte" per performGlobalAction
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * Chiamato dall'app per spegnere lo schermo come fa il pulsante power.
     * Restituisce true se l'azione è andata a buon fine.
     */
    fun lockScreen(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        @Volatile
        var instance: SpeedAccessibilityService? = null
            private set

        /** Verifica se il service è attivo (l'utente ha abilitato l'accessibility) */
        fun isEnabled(): Boolean = instance != null
    }
}
