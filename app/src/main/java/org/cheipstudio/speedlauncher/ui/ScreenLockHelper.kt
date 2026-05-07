package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import org.cheipstudio.speedlauncher.SpeedAdminReceiver
import org.cheipstudio.speedlauncher.R

/**
 * v18: blocco schermo via Device Admin.
 * Alla prima invocazione, se non siamo admin, lanciamo l'intent di sistema per chiedere
 * all'utente di abilitare lo speed launcher come device admin.
 */
object ScreenLockHelper {

    fun lockScreen(activity: Activity): Boolean {
        val ctx = activity.applicationContext
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, SpeedAdminReceiver::class.java)
        return if (dpm.isAdminActive(admin)) {
            try {
                dpm.lockNow()
                true
            } catch (_: Throwable) { false }
        } else {
            requestAdmin(activity, admin)
            false
        }
    }

    private fun requestAdmin(activity: Activity, admin: ComponentName) {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    activity.getString(R.string.screen_lock_admin_explanation)
                )
            }
            activity.startActivity(intent)
            Toast.makeText(
                activity, R.string.screen_lock_enable_admin_toast,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Throwable) {}
    }
}
