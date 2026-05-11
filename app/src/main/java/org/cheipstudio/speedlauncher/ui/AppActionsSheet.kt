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
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo

class AppActionsSheet : BottomSheetDialogFragment() {

    var onPinToggle: ((AppInfo) -> Unit)? = null
    var isPinned: ((AppInfo) -> Boolean)? = null

    private var app: AppInfo? = null
    private var fromDrawer: Boolean = false

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

        // v24: Hide / Show toggle
        // v303: visibile SOLO se aperto dal drawer (non ha senso da home/dock/folder)
        val settings = SpeedApp.instance.settingsRepository
        val hideLabel = view.findViewById<TextView>(R.id.hideLabel)
        val actionHide = view.findViewById<View>(R.id.actionHide)
        val isHidden = settings.isAppHidden(a.key)
        if (!fromDrawer || settings.drawerEnabled.value == false) {
            actionHide.visibility = View.GONE
        } else {
            hideLabel.text = getString(if (isHidden) R.string.action_unhide else R.string.action_hide)
            actionHide.setOnClickListener {
                if (isHidden) settings.unhideApp(a.key) else settings.hideApp(a.key)
                dismissAllowingStateLoss()
            }
        }

        view.findViewById<View>(R.id.actionUninstall).setOnClickListener {
            try {
                // v183: ACTION_DELETE deprecato API 29+, uso UNINSTALL_PACKAGE
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:${a.packageName}")
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    // Fallback: ACTION_DELETE (più compatibile)
                    val fallback = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${a.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallback)
                }
            } catch (t: Throwable) {
                android.widget.Toast.makeText(requireContext(),
                    "Impossibile disinstallare: ${t.message?.take(60)}",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            dismissAllowingStateLoss()
        }
    }

    companion object {
        fun newInstance(app: AppInfo, fromDrawer: Boolean = false): AppActionsSheet {
            return AppActionsSheet().apply { 
                this.app = app
                this.fromDrawer = fromDrawer
            }
        }
    }
}
