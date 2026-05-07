package org.cheipstudio.speedlauncher.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.data.AppInfo

class AppActionsSheet : BottomSheetDialogFragment() {

    var onPinHomeToggle: ((AppInfo) -> Unit)? = null
    var onPinDockToggle: ((AppInfo) -> Unit)? = null
    var onMoveStart: ((AppInfo) -> Unit)? = null
    var isInGrid: ((AppInfo) -> Boolean)? = null
    var isInDock: ((AppInfo) -> Boolean)? = null

    private var app: AppInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_app_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = app ?: return run { dismissAllowingStateLoss() }

        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(a.icon)
        view.findViewById<TextView>(R.id.appLabel).text = a.label

        // Pin home
        val pinHomeLabel = view.findViewById<TextView>(R.id.pinHomeLabel)
        val inGrid = isInGrid?.invoke(a) ?: false
        pinHomeLabel.text = getString(if (inGrid) R.string.action_unpin_home else R.string.action_pin_home)
        view.findViewById<View>(R.id.actionPinHome).setOnClickListener {
            onPinHomeToggle?.invoke(a); dismissAllowingStateLoss()
        }

        // Pin dock
        val pinDockLabel = view.findViewById<TextView>(R.id.pinDockLabel)
        val inDock = isInDock?.invoke(a) ?: false
        pinDockLabel.text = getString(if (inDock) R.string.action_unpin_dock else R.string.action_pin_dock)
        view.findViewById<View>(R.id.actionPinDock).setOnClickListener {
            onPinDockToggle?.invoke(a); dismissAllowingStateLoss()
        }

        // Move (drag)
        val actionMove = view.findViewById<View>(R.id.actionMove)
        if (inGrid || inDock) {
            actionMove.visibility = View.VISIBLE
            actionMove.setOnClickListener {
                dismissAllowingStateLoss()
                // ritardo per dare tempo al sheet di chiudersi
                actionMove.postDelayed({ onMoveStart?.invoke(a) }, 250)
            }
        } else {
            actionMove.visibility = View.GONE
        }

        // Info
        view.findViewById<View>(R.id.actionInfo).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${a.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Throwable) {}
            dismissAllowingStateLoss()
        }

        // Uninstall
        view.findViewById<View>(R.id.actionUninstall).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${a.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Throwable) {}
            dismissAllowingStateLoss()
        }
    }

    companion object {
        fun newInstance(app: AppInfo): AppActionsSheet {
            return AppActionsSheet().apply { this.app = app }
        }
    }
}
