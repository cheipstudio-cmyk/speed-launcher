package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedAccessibilityService
import org.cheipstudio.speedlauncher.SpeedAdminReceiver

/**
 * v31: blocco schermo con doppia strategia.
 *
 * Strategia preferita: AccessibilityService (GLOBAL_ACTION_LOCK_SCREEN, API 28+).
 * Si comporta come la pressione del pulsante power → l'impronta sblocca normalmente.
 * Richiede attivazione dell'accessibility service da parte dell'utente.
 *
 * Fallback: DevicePolicyManager.lockNow() (Device Admin).
 * ATTENZIONE: questa modalità forza il PIN al successivo sblocco — è una restrizione
 * di sicurezza Android e NON si può bypassare lato app. Per questo è solo fallback.
 */
object ScreenLockHelper {

    /**
     * Prova a spegnere lo schermo. Restituisce true se l'azione è partita.
     * - Se l'AccessibilityService è abilitato → lo usa (no PIN forzato)
     * - Altrimenti chiede all'utente di abilitarlo, NON usa il fallback admin
     *   per evitare di costringere il PIN.
     */
    fun lockScreen(activity: Activity): Boolean {
        // strategia 1: accessibility service (preferita)
        val service = SpeedAccessibilityService.instance
        if (service != null) {
            return service.lockScreen()
        }
        // accessibility non abilitato → mostro il dialog di attivazione
        showAccessibilityDialog(activity)
        return false
    }

    /**
     * Chiede all'utente di abilitare l'accessibility service, spiegando il motivo.
     */
    private fun showAccessibilityDialog(activity: Activity) {
        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.accessibility_dialog_title)
                .setMessage(R.string.accessibility_dialog_message)
                .setPositiveButton(R.string.accessibility_dialog_open) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(intent)
                        Toast.makeText(
                            activity, R.string.accessibility_toast_find_speed,
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (_: Throwable) {}
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (_: Throwable) {}
    }

    /**
     * Vecchia strategia: device admin. NON usata di default in v31 perché forza PIN.
     * La lasciamo come API privata in caso serva in futuro.
     */
    @Suppress("unused")
    private fun lockViaAdmin(activity: Activity): Boolean {
        val ctx = activity.applicationContext
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, SpeedAdminReceiver::class.java)
        return if (dpm.isAdminActive(admin)) {
            try {
                dpm.lockNow()
                true
            } catch (_: Throwable) { false }
        } else {
            false
        }
    }
}
