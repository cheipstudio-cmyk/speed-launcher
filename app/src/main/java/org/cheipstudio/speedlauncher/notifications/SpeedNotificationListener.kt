package org.cheipstudio.speedlauncher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * Service per leggere quante notifiche attive ha ogni package.
 * Richiede permesso speciale: l'utente DEVE attivarlo in
 * Impostazioni → Notifiche → Accesso alle notifiche.
 *
 * Mostra solo il "dot" — non leggiamo né mostriamo il contenuto.
 */
class SpeedNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebuildState()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        rebuildState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        rebuildState()
    }

    private fun rebuildState() {
        val active = try {
            activeNotifications ?: return
        } catch (t: SecurityException) {
            // non ancora connesso
            return
        }
        val counts = HashMap<String, Int>()
        for (sbn in active) {
            if (sbn.isOngoing) continue // ignora foreground service notifications
            val pkg = sbn.packageName ?: continue
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        SpeedApp.instance.notificationCounter.update(counts)
    }
}
