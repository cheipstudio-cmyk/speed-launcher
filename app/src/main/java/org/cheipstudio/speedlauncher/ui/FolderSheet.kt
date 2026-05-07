package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.SettingsRepository

/**
 * v19: cartelle full-screen con BLUR trasparente del wallpaper sottostante (API 31+).
 * Il dialog NON è più una BottomSheet, è un Dialog full-screen con sfondo trasparente
 * e RenderEffect.createBlurEffect applicato al decor dell'Activity.
 */
object FolderSheet {

    fun show(
        context: Context,
        folder: HomeItem,
        onLaunch: (AppInfo) -> Unit,
        onRename: (String) -> Unit,
        onRemoveFromFolder: (AppInfo) -> Unit,
        onDeleteFolder: () -> Unit
    ) {
        val activity = context as? Activity ?: return
        val density = context.resources.displayMetrics.density

        // root: contenitore full screen con dim semi-trasparente
        val rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#33000000"))
            setPadding(
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt()
            )
        }

        // Card con la cartella vera e propria
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_folder_panel)
            elevation = 24 * density
            setPadding(
                (24 * density).toInt(), (16 * density).toInt(),
                (24 * density).toInt(), (24 * density).toInt()
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        // handle
        val handle = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        card.addView(handle)

        val nameInput = EditText(context).apply {
            setText(folder.name)
            textSize = 24f
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            background = null
            setHintTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            hint = context.getString(R.string.folder_name_hint)
            setSingleLine(true)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onRename(s?.toString() ?: "") }
            })
        }
        card.addView(nameInput)

        val apps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val byKey = apps.associateBy { it.key }
        val folderApps = folder.folderApps.mapNotNull { byKey[it] }

        val grid = GridLayout(context).apply {
            columnCount = 4
            useDefaultMargins = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        card.addView(grid)
        // popolato sotto dopo aver creato il dialog

        val deleteBtn = TextView(context).apply {
            text = context.getString(R.string.folder_delete)
            setTextColor(Color.parseColor("#FF8A8A"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_folder_delete_btn)
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (12 * density).toInt())
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (20 * density).toInt()
            layoutParams = lp
        }
        card.addView(deleteBtn)

        rootContainer.addView(card)

        // Dialog full-screen senza dim (lo applichiamo noi via blur)
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
            window?.let { w ->
                w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            setContentView(rootContainer)
        }

        // BLUR del decor sottostante (API 31+)
        val decor = activity.window?.decorView

        // v21: popola grid con onLaunch che dismiss anche il dialog
        val onLaunchAndDismiss: (AppInfo) -> Unit = { app ->
            onLaunch(app)
            try { dialog.dismiss() } catch (_: Throwable) {}
        }
        for (app in folderApps) grid.addView(buildAppCell(context, app, onLaunchAndDismiss, onRemoveFromFolder))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
            try {
                val blur = RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
                decor.setRenderEffect(blur)
            } catch (_: Throwable) {}
        }

        dialog.setOnDismissListener {
            // rimuovi blur
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                try { decor.setRenderEffect(null) } catch (_: Throwable) {}
            }
        }

        // tap fuori dalla card chiude
        rootContainer.setOnClickListener { dialog.dismiss() }
        card.setOnClickListener { /* swallow */ }

        deleteBtn.setOnClickListener { onDeleteFolder(); dialog.dismiss() }
        dialog.show()
    }

    private fun resolveAttr(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun buildAppCell(
        context: Context,
        app: AppInfo,
        onLaunch: (AppInfo) -> Unit,
        onRemoveFromFolder: (AppInfo) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true; isFocusable = true
            background = null  // v21: nessun bg sulla cella, il ripple sta sull'icona
        }
        val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
        lp.width = 0; lp.height = (96 * density).toInt()
        cell.layoutParams = lp

        val settings = SpeedApp.instance.settingsRepository
        val shape = settings.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL

        // v21: wrapper con ripple rotondo invece di rettangolo giallo
        val iconWrap = android.widget.FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_app_icon_ripple)
            val s = (54 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            isClickable = false
            isFocusable = false
        }
        val icon = ImageView(context).apply {
            setImageDrawable(IconShaper.shape(app.icon, shape))
            val s = (44 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(s, s, Gravity.CENTER)
        }
        iconWrap.addView(icon)
        cell.addView(iconWrap)

        val label = TextView(context).apply {
            text = app.label
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = (4 * density).toInt()
            layoutParams = tlp
        }
        cell.addView(label)

        cell.setOnClickListener { onLaunch(app) }
        cell.setOnLongClickListener {
            onRemoveFromFolder(app)
            true
        }
        return cell
    }
}
