package org.cheipstudio.speedlauncher.data

import android.graphics.drawable.Drawable
import android.os.UserHandle

/**
 * Informazioni su un'app installata visibile nel launcher.
 * v102: ricreato dopo perdita del file (apparentemente perso in qualche commit precedente).
 */
data class AppInfo(
    val packageName: String,
    val componentName: String,
    val label: String,
    val icon: Drawable,
    val userHandle: UserHandle
) {
    /** Chiave univoca per identificare l'app (pkg/component) */
    val key: String get() = "$packageName/$componentName"
}
