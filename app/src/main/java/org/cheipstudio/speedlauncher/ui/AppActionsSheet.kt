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

    var onPinToggle: ((AppInfo) -> Unit)? = null
    var isPinned: ((AppInfo) -> Boolean)? = null

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

        val pinLabel = view.findViewById<TextView>(R.id.pinLabel)
        val pinned = isPinned?.invoke(a) ?: false
        pinLabel.text = getString(if (pinned) R.string.action_unpin else R.string.action_pin)

        view.findViewById<View>(R.id.actionPin).setOnClickListener {
            onPinToggle?.invoke(a)
            dismissAllowingStateLoss()
        }
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
