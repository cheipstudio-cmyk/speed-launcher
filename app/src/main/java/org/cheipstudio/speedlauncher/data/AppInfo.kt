package org.cheipstudio.speedlauncher.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val componentName: String,
    val label: String,
    val icon: Drawable,
    val userHandle: android.os.UserHandle
) {
    val key: String get() = "$packageName/$componentName"
}
