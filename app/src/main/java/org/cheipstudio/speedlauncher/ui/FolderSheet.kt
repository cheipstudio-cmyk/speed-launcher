package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
 * v25: cartelle stile Pixel/Android puro.
 * - Card grande con corner radius 32dp
 * - Titolo cartella in alto, sotto griglia icone con padding generoso
 * - Bottone "Elimina" stile chip Pixel sotto la griglia
 * - Niente glitch tastiera (focus solo a tap)
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

        val rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#33000000"))
            setPadding(
                (20 * density).toInt(), (20 * density).toInt(),
                (20 * density).toInt(), (20 * density).toInt()
            )
        }

        val bgStyle = SpeedApp.instance.settingsRepository.folderBgStyle.value
            ?: SettingsRepository.FOLDER_BG_SYSTEM
        val cardBgRes = when (bgStyle) {
            SettingsRepository.FOLDER_BG_TRANSPARENT -> R.drawable.bg_folder_panel_transparent
            SettingsRepository.FOLDER_BG_DARK -> R.drawable.bg_folder_panel_dark
            SettingsRepository.FOLDER_BG_LIGHT -> R.drawable.bg_folder_panel_light
            else -> R.drawable.bg_folder_panel
        }
        val isDarkBg = bgStyle == SettingsRepository.FOLDER_BG_DARK ||
                bgStyle == SettingsRepository.FOLDER_BG_TRANSPARENT
        val isLightBg = bgStyle == SettingsRepository.FOLDER_BG_LIGHT
        val textColor = when {
            isDarkBg -> Color.WHITE
            isLightBg -> Color.parseColor("#1A1A1A")
            else -> resolveAttr(context, com.google.android.material.R.attr.colorOnSurface)
        }
        val hintColor = when {
            isDarkBg -> Color.parseColor("#88FFFFFF")
            isLightBg -> Color.parseColor("#88000000")
            else -> resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        val deleteChipBg = when {
            isDarkBg -> Color.parseColor("#22FFFFFF")
            isLightBg -> Color.parseColor("#11000000")
            else -> resolveAttr(context, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, cardBgRes)
            elevation = 24 * density
            setPadding(
                (28 * density).toInt(), (32 * density).toInt(),
                (28 * density).toInt(), (28 * density).toInt()
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        // Title input - tap per editare
        val nameInput = EditText(context).apply {
            setText(folder.name)
            textSize = 20f
            setTextColor(textColor)
            background = null
            setHintTextColor(hintColor)
            hint = context.getString(R.string.folder_name_hint)
            setSingleLine(true)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            // v31.2: la tastiera NON deve apparire automaticamente quando il dialog si apre
            showSoftInputOnFocus = false
            setOnClickListener {
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = true
                showSoftInputOnFocus = true
                requestFocus()
                setSelection(text?.length ?: 0)
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (24 * density).toInt()
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
            columnCount = if (folderApps.size <= 4) folderApps.size.coerceAtLeast(1) else 4
            useDefaultMargins = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        card.addView(grid)

        // Chip "Elimina cartella" stile Pixel
        val deleteChip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100 * density
                setColor(deleteChipBg)
            }
            setPadding((20 * density).toInt(), (12 * density).toInt(),
                (24 * density).toInt(), (12 * density).toInt())
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (24 * density).toInt()
            layoutParams = lp
        }
        val trashIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(textColor)
            val s = (18 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (10 * density).toInt()
            }
        }
        val trashLabel = TextView(context).apply {
            text = context.getString(R.string.folder_delete)
            setTextColor(textColor)
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        deleteChip.addView(trashIcon)
        deleteChip.addView(trashLabel)
        card.addView(deleteChip)

        rootContainer.addView(card)

        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
            window?.let { w ->
                w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                )
            }
            setContentView(rootContainer)
        }

        val decor = activity.window?.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
            try {
                val blur = RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
                decor.setRenderEffect(blur)
            } catch (_: Throwable) {}
        }

        val onLaunchAndDismiss: (AppInfo) -> Unit = { app ->
            onLaunch(app)
            try { dialog.dismiss() } catch (_: Throwable) {}
        }
        for (app in folderApps) grid.addView(buildAppCell(context, app, onLaunchAndDismiss, onRemoveFromFolder, textColor))

        dialog.setOnDismissListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            try { imm?.hideSoftInputFromWindow(nameInput.windowToken, 0) } catch (_: Throwable) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                try { decor.setRenderEffect(null) } catch (_: Throwable) {}
            }
        }

        rootContainer.setOnClickListener { dialog.dismiss() }
        card.setOnClickListener { /* swallow */ }

        deleteChip.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            )
                .setTitle(R.string.folder_delete_confirm_title)
                .setMessage(R.string.folder_delete_confirm_msg)
                .setPositiveButton(R.string.folder_delete) { _, _ ->
                    onDeleteFolder(); dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
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
        onRemoveFromFolder: (AppInfo) -> Unit,
        labelTextColor: Int
    ): View {
        val density = context.resources.displayMetrics.density
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true; isFocusable = true
            background = null
        }
        val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
        lp.width = 0; lp.height = (108 * density).toInt()
        cell.layoutParams = lp

        val settings = SpeedApp.instance.settingsRepository
        val shape = settings.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL

        val iconWrap = android.widget.FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_app_icon_ripple)
            val s = (60 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        val icon = ImageView(context).apply {
            setImageDrawable(IconShaper.shape(app.icon, shape))
            val s = (50 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(s, s, Gravity.CENTER)
        }
        iconWrap.addView(icon)
        cell.addView(iconWrap)

        val label = TextView(context).apply {
            text = app.label
            setTextColor(labelTextColor)
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = (6 * density).toInt()
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
