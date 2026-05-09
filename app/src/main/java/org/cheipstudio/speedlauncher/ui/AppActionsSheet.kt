package org.cheipstudio.speedlauncher.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo

/**
 * v149: BottomSheet con azioni su app (info / disinstalla / pin / nascondi).
 * Design coerente con WidgetRemoveSheet / FolderSheet menu.
 */
class AppActionsSheet : BottomSheetDialogFragment() {

    private var app: AppInfo? = null
    var isPinned: ((AppInfo) -> Boolean)? = null
    var onPinToggle: ((AppInfo) -> Unit)? = null

    companion object {
        private const val ARG_PKG = "pkg"
        private const val ARG_KEY = "key"
        private const val ARG_LABEL = "label"

        fun newInstance(app: AppInfo): AppActionsSheet {
            val s = AppActionsSheet()
            val args = Bundle()
            args.putString(ARG_PKG, app.packageName)
            args.putString(ARG_KEY, app.key)
            args.putString(ARG_LABEL, app.label)
            s.arguments = args
            return s
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density

        val pkg = arguments?.getString(ARG_PKG) ?: ""
        val key = arguments?.getString(ARG_KEY) ?: ""
        val label = arguments?.getString(ARG_LABEL) ?: ""

        // Recupero AppInfo aggiornato dal repository
        app = SpeedApp.instance.appRepository.apps.value
            ?.firstOrNull { it.key == key || it.packageName == pkg }

        val container_ll = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (16 * density).toInt(),
                (24 * density).toInt(), (24 * density).toInt()
            )
        }

        // Drag handle
        val handle = View(ctx).apply {
            background = ctx.getDrawable(R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container_ll.addView(handle)

        // Header con icona + nome
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        val iconView = ImageView(ctx).apply {
            setImageDrawable(app?.icon)
            val s = (48 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (16 * density).toInt()
            }
        }
        header.addView(iconView)
        val titleView = TextView(ctx).apply {
            text = app?.label ?: label
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            ellipsize = android.text.TextUtils.TruncateAt.END
            setSingleLine(true)
        }
        header.addView(titleView)
        container_ll.addView(header)

        // Azione: Pin / Unpin home
        val pinned = app?.let { isPinned?.invoke(it) } ?: false
        val pinLabel = if (pinned) getString(R.string.action_unpin_home) else getString(R.string.action_pin_home)
        val pinIcon = if (pinned) R.drawable.ic_delete_forever else R.drawable.ic_home_outline
        container_ll.addView(buildRow(ctx, density, pinLabel, pinIcon, false) {
            app?.let { onPinToggle?.invoke(it) }
            dismiss()
        })

        // Azione: Info app
        container_ll.addView(buildRow(ctx, density, getString(R.string.action_app_info), R.drawable.ic_info_outline, false) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (_: Throwable) {}
            dismiss()
        })

        // Azione: Disinstalla (solo se non system app)
        val isSystem = isSystemApp(pkg)
        if (!isSystem) {
            container_ll.addView(buildRow(ctx, density, getString(R.string.action_uninstall), R.drawable.ic_delete_forever, true) {
                try {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                } catch (_: Throwable) {}
                dismiss()
            })
        }

        // Azione: Nascondi
        val hidden = SpeedApp.instance.settingsRepository.hiddenApps.value ?: emptySet()
        val isHidden = hidden.contains(key)
        val hideLabel = if (isHidden) getString(R.string.action_unhide) else getString(R.string.action_hide)
        container_ll.addView(buildRow(ctx, density, hideLabel, R.drawable.ic_visibility_off, false) {
            if (isHidden) SpeedApp.instance.settingsRepository.unhideApp(key)
            else SpeedApp.instance.settingsRepository.hideApp(key)
            dismiss()
        })

        return container_ll
    }

    private fun buildRow(
        ctx: android.content.Context,
        density: Float,
        label: String,
        iconRes: Int,
        isDestructive: Boolean,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
        }
        val icon = ImageView(ctx).apply {
            setImageResource(iconRes)
            val s = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (16 * density).toInt()
            }
            setColorFilter(
                if (isDestructive) android.graphics.Color.parseColor("#E53935")
                else resolveAttr(com.google.android.material.R.attr.colorOnSurface)
            )
        }
        row.addView(icon)
        val txt = TextView(ctx).apply {
            text = label
            textSize = 16f
            setTextColor(
                if (isDestructive) android.graphics.Color.parseColor("#E53935")
                else resolveAttr(com.google.android.material.R.attr.colorOnSurface)
            )
            if (isDestructive) setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(txt)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun isSystemApp(pkg: String): Boolean {
        return try {
            val pm = requireContext().packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        } catch (_: Throwable) { false }
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
