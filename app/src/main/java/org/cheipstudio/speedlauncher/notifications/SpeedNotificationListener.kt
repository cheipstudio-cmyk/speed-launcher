package org.cheipstudio.speedlauncher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * Service per leggere quante notifiche attive ha ogni package.
 * Richiede permesso speciale: l'utente DEVE attivarlo in
 * Impostazioni → Notifiche → Accesso alle notifiche.
 *
 * Mostra solo il "dot" — non leggiamo né mostriamo il contenuto.
 *
 * v95: fix conteggio "+1". Android emette per ogni gruppo di notifiche
 * una "summary notification" oltre alle notifiche figlie. Senza filtro,
 * 1 notifica WhatsApp veniva contata come 2 (figlia + summary).
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
            // v95: filtri multipli per evitare doppio conteggio
            // 1. ignora foreground service (sempre persistenti, non sono "messaggi")
            if (sbn.isOngoing) continue

            val notification = sbn.notification ?: continue
            val flags = notification.flags

            // 2. ignora group summary (Android li crea automaticamente per app
            //    che inviano notifiche in gruppi: WhatsApp, Telegram, Gmail ecc.)
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue

            // 3. ignora media/now-playing (non sono messaggi azionabili)
            if ((flags and Notification.FLAG_NO_CLEAR) != 0 &&
                (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) continue

            // 4. ignora notifiche non clearable di sistema
            if ((flags and Notification.FLAG_LOCAL_ONLY) != 0 && sbn.packageName == "android") continue

            val pkg = sbn.packageName ?: continue
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        SpeedApp.instance.notificationCounter.update(counts)
    }
}
