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
    /** v132: ref al dialog corrente per chiusura/riapertura programmatica dopo modifica */
    private var currentDialog: android.app.Dialog? = null
    private var currentReopenInfo: Triple<Context, HomeItem, FolderCallbacks>? = null
    
    /** v132: callbacks come dataclass per riapertura */
    data class FolderCallbacks(
        val onLaunch: (AppInfo) -> Unit,
        val onRename: (String) -> Unit,
        val onRemoveFromFolder: (AppInfo) -> Unit,
        val onDeleteFolder: () -> Unit
    )
    
    /** v132: chiude la cartella attuale e la riapre con la versione aggiornata del folder. */
    fun reopen(folderUpdated: HomeItem) {
        val info = currentReopenInfo ?: return
        val (ctx, _, callbacks) = info
        try { currentDialog?.dismiss() } catch (_: Throwable) {}
        currentDialog = null
        currentReopenInfo = null
        // Riapri con il folder aggiornato dopo un piccolo delay (per animation dismiss)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            show(ctx, folderUpdated, callbacks.onLaunch, callbacks.onRename, callbacks.onRemoveFromFolder, callbacks.onDeleteFolder)
        }, 100)
    }

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

        // v32: TextView + dialog di rename separato. Zero glitch tastiera.
        val nameLabel = TextView(context).apply {
            text = folder.name
            textSize = 20f
            setTextColor(textColor)
            setSingleLine(true)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = null
            isClickable = true
            isFocusable = true
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                showRenameDialog(context, this, folder.name) { newName ->
                    text = newName
                    onRename(newName)
                }
            }
        }
        card.addView(nameLabel)

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

        // v132: chip "Elimina cartella" rimosso da dentro la cartella.
        // Si elimina dal long press della cartella in home → menu

        rootContainer.addView(card)

        // v132: salvo info per riapertura programmatica
        currentReopenInfo = Triple(context, folder, FolderCallbacks(onLaunch, onRename, onRemoveFromFolder, onDeleteFolder))
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
            window?.let { w ->
                w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                // v54: nav bar trasparente per non avere stacco col wallpaper
                w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                w.statusBarColor = Color.TRANSPARENT
                w.navigationBarColor = Color.TRANSPARENT
                // Estendi sotto la nav bar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    w.setDecorFitsSystemWindows(false)
                } else {
                    @Suppress("DEPRECATION")
                    w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
                w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                )
            }
            setContentView(rootContainer)
        }

        // v54: pre-set initial state PRIMA del dialog.show() per evitare il flash di un frame
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        // v141: forzo pivot al CENTRO della card per animazione che parte dal centro,
        // non dall'angolo (default su LinearLayout senza misure)
        card.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.pivotX = card.width / 2f
                card.pivotY = card.height / 2f
                card.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        rootContainer.alpha = 0f

        val decor = activity.window?.decorView
        // v49: blur applicato con animazione DURANTE l'apertura del dialog (no più glitch al pre-show)

        // v49: helper per chiudere fluido con animazione di uscita + blur che svanisce
        fun closeFolder() {
            // Blur fade out parallelo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                val blurOut = android.animation.ValueAnimator.ofFloat(40f, 0f).apply {
                    duration = 180
                    interpolator = android.view.animation.AccelerateInterpolator()
                    addUpdateListener { v ->
                        try {
                            val r = v.animatedValue as Float
                            if (r > 0.5f) {
                                val blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                                decor.setRenderEffect(blur)
                            } else {
                                decor.setRenderEffect(null)
                            }
                        } catch (_: Throwable) {}
                    }
                }
                blurOut.start()
            }
            card.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(180)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                        try { decor.setRenderEffect(null) } catch (_: Throwable) {}
                    }
                    try { dialog.dismiss() } catch (_: Throwable) {}
                }
                .start()
        }

        val onLaunchAndDismiss: (AppInfo) -> Unit = { app ->
            onLaunch(app)
            closeFolder()
        }
        for (app in folderApps) grid.addView(buildAppCell(context, app, onLaunchAndDismiss, onRemoveFromFolder, textColor))

        dialog.setOnDismissListener {
            // safety net: rimuovi blur al dismiss
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                try { decor.setRenderEffect(null) } catch (_: Throwable) {}
            }
            // v132: azzera ref se questo dialog era quello corrente
            if (currentDialog === dialog) {
                currentDialog = null
                currentReopenInfo = null
            }
        }

        currentDialog = dialog
        rootContainer.setOnClickListener { closeFolder() }
        card.setOnClickListener { /* swallow */ }

        // v132: deleteChip listener rimosso (chip eliminato dalla UI)
        // v49+v54: animazione di entrata fluida (scale + fade) + blur progressivo
        // I valori iniziali sono già settati PRIMA del show() per evitare il flash
        dialog.setOnShowListener {
            // Backdrop fade in
            rootContainer.animate()
                .alpha(1f)
                .setDuration(140)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            // Card scale + fade in
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                .start()
            // v49+v140: blur in solo se toggle on
            val blurOn = SpeedApp.instance.settingsRepository.blurFolder.value != false
            if (blurOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && decor != null) {
                val anim = android.animation.ValueAnimator.ofFloat(0f, 40f).apply {
                    duration = 220
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { v ->
                        try {
                            val r = v.animatedValue as Float
                            if (r > 0.5f) {
                                val blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                                decor.setRenderEffect(blur)
                            }
                        } catch (_: Throwable) {}
                    }
                }
                anim.start()
            }
        }
        dialog.show()
    }


    /**
     * v95: attacca un overlay drawable per disegnare dot/count notifiche
     * sull'icona di un'app dentro la cartella aperta.
     */
    private fun attachNotificationBadge(iconView: android.widget.ImageView, packageName: String) {
        val ctx = iconView.context
        val badge = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val count = SpeedApp.instance.notificationCounter.countFor(packageName)
                if (count <= 0) return
                val s = SpeedApp.instance.settingsRepository
                val mode = s.notificationBadgeMode.value ?: org.cheipstudio.speedlauncher.data.SettingsRepository.BADGE_DOT
                if (mode == org.cheipstudio.speedlauncher.data.SettingsRepository.BADGE_OFF) return

                val color = s.dotColor.value ?: org.cheipstudio.speedlauncher.data.SettingsRepository.DOT_DEFAULT

                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                }
                val w = bounds.width().toFloat()

                if (mode == org.cheipstudio.speedlauncher.data.SettingsRepository.BADGE_COUNT) {
                    val text = if (count > 99) "99+" else count.toString()
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = w * 0.22f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    val tw = textPaint.measureText(text)
                    val ph = w * 0.30f
                    val pw = (tw + w * 0.18f).coerceAtLeast(ph)
                    val rect = android.graphics.RectF(w - pw - 2f, 2f, w - 2f, ph + 2f)
                    canvas.drawRoundRect(rect, ph / 2, ph / 2, paint)
                    val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                    canvas.drawText(text, rect.centerX(), baseline, textPaint)
                } else {
                    val r = w * 0.10f
                    canvas.drawCircle(w - r - 2f, r + 2f, r, paint)
                }
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
        iconView.overlay.add(badge)
        iconView.viewTreeObserver.addOnGlobalLayoutListener {
            badge.setBounds(0, 0, iconView.width, iconView.height)
        }
        // v95: observer lifecycle-aware via attach/detach listener della view
        val obs = androidx.lifecycle.Observer<Map<String, Int>> { iconView.invalidate() }
        iconView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                try {
                    SpeedApp.instance.notificationCounter.counts.observeForever(obs)
                } catch (_: Throwable) {}
            }
            override fun onViewDetachedFromWindow(v: android.view.View) {
                try {
                    SpeedApp.instance.notificationCounter.counts.removeObserver(obs)
                } catch (_: Throwable) {}
            }
        })
        // Se già attaccata, registro subito
        if (iconView.isAttachedToWindow) {
            try {
                SpeedApp.instance.notificationCounter.counts.observeForever(obs)
            } catch (_: Throwable) {}
        }
    }

    private fun resolveAttr(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /**
     * v32: dialog separato per rinominare la cartella.
     * Apre solo questo quando l'utente tappa il nome — nessuna tastiera nel dialog principale.
     */
    private fun showRenameDialog(
        context: Context,
        labelView: TextView,
        currentName: String,
        onConfirm: (String) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val container = android.widget.FrameLayout(context).apply {
            val pad = (24 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
        }
        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            setText(currentName)
            setSelection(currentName.length)
            setSingleLine(true)
            textSize = 18f
            hint = context.getString(R.string.folder_name_hint)
        }
        val til = com.google.android.material.textfield.TextInputLayout(
            context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            setBoxCornerRadii(20f * density, 20f * density, 20f * density, 20f * density)
            addView(input)
        }
        container.addView(til)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.folder_rename_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm(input.text?.toString() ?: "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // Apri tastiera dopo che il dialog è visibile (no glitch perché il dialog di rename È quello dell'input)
        input.requestFocus()
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
            setImageDrawable(IconShaper.shape(app.icon, shape, context, app.packageName, app.componentName))
            val s = (50 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(s, s, Gravity.CENTER)
        }
        iconWrap.addView(icon)
        // v95: badge notifiche sull\'icona dentro la cartella aperta
        attachNotificationBadge(icon, app.packageName)
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
