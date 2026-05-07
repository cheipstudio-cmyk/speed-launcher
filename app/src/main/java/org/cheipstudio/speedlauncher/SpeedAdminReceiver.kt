package org.cheipstudio.speedlauncher

import android.app.admin.DeviceAdminReceiver

/**
 * v18: receiver per il blocco schermo via DevicePolicyManager.lockNow().
 * Dichiarato nel manifest con BIND_DEVICE_ADMIN. Vuoto perché lockNow è l'unica feature usata.
 */
class SpeedAdminReceiver : DeviceAdminReceiver()
