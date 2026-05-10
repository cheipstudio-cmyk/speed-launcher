package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.SettingsRepository

/**
 * v37: dock-style raccomandate. Card con corner 28dp simile alla search bar,
 * 4 o 5 icone in fila orizzontale (configurabile). Niente header testuale: la card stessa
 * comunica visivamente il dock.
 */
class RecommendedView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongPress: ((AppInfo) -> Unit)? = null
    var onContainerLongPress: (() -> Unit)? = null  // v226: long press sul vuoto della dock
    
    // v227: rilevazione long press su tutta la dock (anche sopra le icone)
    private var dockLongPressDownX = 0f
    private var dockLongPressDownY = 0f
    private val dockLongPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var dockLongPressFired = false
    private val dockLongPressRunnable = Runnable {
        dockLongPressFired = true
        // v241: haptic via HapticHelper (rispetta hapticEnabled setting)
        try { HapticHelper.longPress(this) } catch (_: Throwable) {}
        onContainerLongPress?.invoke()
    }
    private val dockLongPressSlop by lazy {
        android.view.ViewConfiguration.get(context).scaledTouchSlop * 4  // v241: più tollerante allo scroll
    }

    private val density = resources.displayMetrics.density
    private fun isLand(): Boolean = resources.configuration.orientation == 
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private val card: MaterialCardView
    private val row: LinearLayout

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                dockLongPressDownX = ev.x
                dockLongPressDownY = ev.y
                dockLongPressFired = false
                dockLongPressHandler.removeCallbacks(dockLongPressRunnable)
                dockLongPressHandler.removeCallbacks(dockLongPressArm)
                // v260: warm-up più lungo (180ms) per swipe veloci
                dockLongPressHandler.postDelayed(dockLongPressArm, 180)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(ev.x - dockLongPressDownX)
                val dyRaw = ev.y - dockLongPressDownY  // negativo = swipe up
                val dy = kotlin.math.abs(dyRaw)
                // v260: cancellazione iper-aggressiva per swipe up (1px basta)
                if (dyRaw < -1) {
                    dockLongPressHandler.removeCallbacks(dockLongPressArm)
                    dockLongPressHandler.removeCallbacks(dockLongPressRunnable)
                } else if (dx > dockLongPressSlop || dy > dockLongPressSlop) {
                    dockLongPressHandler.removeCallbacks(dockLongPressArm)
                    dockLongPressHandler.removeCallbacks(dockLongPressRunnable)
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                dockLongPressHandler.removeCallbacks(dockLongPressArm)
                dockLongPressHandler.removeCallbacks(dockLongPressRunnable)
            }
        }
        if (dockLongPressFired) {
            dockLongPressFired = false
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }
    
    // v260: armatura - dopo 180ms di immobilità posta timer 520ms (totale ~700ms)
    private val dockLongPressArm = Runnable {
        dockLongPressHandler.postDelayed(dockLongPressRunnable, 520)
    }
    
    init {
        // Card wrapper
        card = MaterialCardView(context).apply {
            radius = 28 * density
            cardElevation = 0f
            // v41: sfondo appena percepibile, no stacco grigio
            setCardBackgroundColor(themedBgColor())
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            // v131: hardware layer — applyDrawerSlide fa alpha+scale+translation insieme,
            // accelerare su GPU rende il parallasse più fluido
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val padH = ((if (isLand()) 6 else 8) * density).toInt()
            val padV = ((if (isLand()) 4 else 10) * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(row)
        addView(card)
    }

    private fun resolveAttrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /**
     * v41: colore sfondo dock card "appena percepibile":
     * - alpha 32/255 (~12%) di colorOnSurface
     * - si adatta a chiaro/scuro automaticamente perché colorOnSurface si inverte
     */
    private fun softBgColor(): Int {
        val onSurface = resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
        val r = (onSurface shr 16) and 0xFF
        val g = (onSurface shr 8) and 0xFF
        val b = onSurface and 0xFF
        return Color.argb(32, r, g, b)
    }

    /**
     * v48: colore sfondo dock secondo il dockTheme nelle settings.
     * - "transparent" → totalmente trasparente
     * - "light" → bg chiaro semi-opaco (#F5F5F5 a 90%)
     * - "dark" → bg scuro semi-opaco (#1B1B1F a 90%)
     * - "system" o default → softBgColor (~12% colorOnSurface, segue chiaro/scuro)
     */
    private fun themedBgColor(): Int {
        val theme = SpeedApp.instance.settingsRepository.dockTheme.value ?: "system"
        return when (theme) {
            // v49: stessi identici valori di HomeView.applySearchTheme
            "transparent" -> Color.argb(80, 0, 0, 0)
            "light" -> Color.argb(230, 245, 245, 245)
            "dark" -> Color.argb(230, 27, 27, 31)
            else -> softBgColor()
        }
    }

    /** v48: forza refresh del bg quando il tema cambia */
    fun refreshTheme() {
        val card = (getChildAt(0) as? com.google.android.material.card.MaterialCardView)
        card?.setCardBackgroundColor(themedBgColor())
        card?.cardElevation = 0f
        // Aggiorna anche il color del testo "label" sotto le icone se serve
    }

    /**
     * @param scope "home" (uses dock card) o "drawer" (uses inline icons no card wrapper)
     */
    /**
     * v38: chiamato dal BottomSheet onSlide del drawer.
     * slideOffset varia: -1 (collapsed) → 0 (peek) → 1 (expanded).
     * Effetto: man mano che il drawer si chiude (offset cala), la card svanisce e scivola in alto.
     */
    fun applyDrawerSlide(slideOffset: Float) {
        // v56: effetto parallasse più visibile.
        val s = slideOffset.coerceIn(-1f, 1f)
        val raw = ((s).coerceIn(0f, 1f))
        val visibleProgress = raw
        alpha = 0.3f + 0.7f * visibleProgress
        translationY = (1f - visibleProgress) * (56f * density)
        val sc = 0.85f + 0.15f * visibleProgress
        scaleX = sc; scaleY = sc
    }

    fun refresh(scope: String = "home") {
        val settings = SpeedApp.instance.settingsRepository
        val all = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val hidden = settings.hiddenApps.value ?: emptySet<String>()
        val available = all.filter { !hidden.contains(it.key) }
        val byKey = available.associateBy { it.key }
        val countToShow = settings.recommendedCount.value ?: 5

        // v84: supporta modalità "ai" (default, da usage) o "manual" (scelta utente)
        val mode = settings.recommendedMode.value ?: SettingsRepository.REC_MODE_AI
        var topApps: List<AppInfo> = if (mode == SettingsRepository.REC_MODE_MANUAL) {
            // v132: usa la lista ordinata se presente, altrimenti fallback al Set
            val orderedKeys = settings.recommendedManualOrder.value ?: emptyList()
            if (orderedKeys.isNotEmpty()) {
                orderedKeys.mapNotNull { byKey[it] }.take(countToShow)
            } else {
                val manualKeys = settings.recommendedManualApps.value ?: mutableSetOf()
                manualKeys.mapNotNull { byKey[it] }.take(countToShow)
            }
        } else {
            // Modalità AI: usage tracker
            val tracker = SpeedApp.instance.usageTracker
            val topKeys = tracker.getTopApps(byKey.keys, topN = countToShow)
            topKeys.mapNotNull { byKey[it] }
        }

        // v75: se vuoto (nuovo install / manuale non configurato), fallback alfabetico
        if (topApps.isEmpty() && available.isNotEmpty()) {
            topApps = available.sortedBy { it.label.lowercase() }.take(countToShow)
        }

        if (topApps.isEmpty()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        // colori adattivi
        val labelColor: Int
        val shadow: Boolean
        val showCard: Boolean
        when (scope) {
            "drawer" -> {
                labelColor = resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
                shadow = false
                showCard = false
            }
            else -> {
                labelColor = resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
                shadow = false
                showCard = true
            }
        }
        // v120: card SEMPRE visible — usato come container per row
        // showCard=false significa solo "background trasparente" (come nel drawer),
        // NON "nascondi tutto". Bug precedente: card.GONE nascondeva anche row dentro.
        card.visibility = VISIBLE
        if (!showCard) {
            // Nel drawer: card trasparente, row direttamente visibile
            card.setCardBackgroundColor(Color.TRANSPARENT)
            card.cardElevation = 0f
        } else {
            card.setCardBackgroundColor(themedBgColor())
            card.cardElevation = 0f
        }

        row.removeAllViews()
        val shape = SpeedApp.instance.settingsRepository.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL
        for (app in topApps) {
            row.addView(buildCell(app, labelColor, shadow, shape))
        }
    }

    private fun buildCell(app: AppInfo, labelColor: Int, shadow: Boolean, shape: String): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (4 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_app_icon_ripple_themed)
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cell.layoutParams = lp

        val iconWrap = FrameLayout(context).apply {
            val sz = if (isLand()) (42 * density).toInt() else (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
        val icon = ImageView(context).apply {
            setImageDrawable(IconShaper.shape(app.icon, shape, context, app.packageName, app.componentName))
            val sz = if (isLand()) (36 * density).toInt() else (44 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(sz, sz, Gravity.CENTER)
        }
        iconWrap.addView(icon)
        // v67: badge notifiche sull'icon (overlay drawable, refresh on demand)
        attachNotificationBadge(icon, app.packageName)
        cell.addView(iconWrap)

        val label = TextView(context).apply {
            text = app.label
            setTextColor(labelColor)
            textSize = 10.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            if (shadow) setShadowLayer(2f, 0f, 1f, Color.argb(160, 0, 0, 0))
            includeFontPadding = false
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = (4 * density).toInt()
            layoutParams = tlp
            // v151: rispetta toggle "etichette dock raccomandate"
            visibility = if (SpeedApp.instance.settingsRepository.showDockLabels.value != false)
                android.view.View.VISIBLE else android.view.View.GONE
        }
        cell.addView(label)

        cell.setOnClickListener { onAppClick?.invoke(app) }
        // v87: rimosso onLongClickListener — niente modal sulla dock raccomandate
        return cell
    }

    /**
     * v67: applica overlay badge notifiche sull'icon view (raccomandate dock).
     * Stesso design delle icone home (v67): dot 5dp pulito senza shadow, o pill con count.
     */
    private fun attachNotificationBadge(iconView: ImageView, packageName: String) {
        val badge = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val count = SpeedApp.instance.notificationCounter.countFor(packageName)
                if (count <= 0) return
                val s = SpeedApp.instance.settingsRepository
                val mode = s.notificationBadgeMode.value ?: SettingsRepository.BADGE_DOT
                if (mode == SettingsRepository.BADGE_OFF) return
                val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.dotColor.value ?: SettingsRepository.DOT_DEFAULT
                }
                val b = bounds
                if (mode == SettingsRepository.BADGE_COUNT) {
                    val txt = if (count > 99) "99+" else count.toString()
                    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 9.5f * density
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    }
                    val txtW = tp.measureText(txt)
                    val padH = 5f * density; val padV = 2f * density
                    val pillW = (txtW + padH * 2).coerceAtLeast(16f * density)
                    val pillH = (tp.textSize + padV * 2).coerceAtLeast(16f * density)
                    val cx = b.right - pillW / 2 - 2f * density
                    val cy = b.top + pillH / 2 + 2f * density
                    val rect = android.graphics.RectF(cx - pillW / 2, cy - pillH / 2, cx + pillW / 2, cy + pillH / 2)
                    val cornerR = pillH / 2
                    canvas.drawRoundRect(rect, cornerR, cornerR, dotPaint)
                    val fm = tp.fontMetrics
                    canvas.drawText(txt, cx, cy - (fm.ascent + fm.descent) / 2, tp)
                } else {
                    val r = 5f * density
                    val cx = b.right - r - 2f * density
                    val cy = b.top + r + 2f * density
                    canvas.drawCircle(cx, cy, r, dotPaint)
                }
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(f: android.graphics.ColorFilter?) {}
            @Deprecated("API")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        // Posiziono il drawable a coprire la stessa area dell'iconView
        iconView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (iconView.width > 0) {
                    badge.setBounds(0, 0, iconView.width, iconView.height)
                    iconView.overlay.clear()
                    iconView.overlay.add(badge)
                    try { iconView.viewTreeObserver.removeOnGlobalLayoutListener(this) } catch (_: Throwable) {}
                }
            }
        })
    }

    /** v67: chiama questa per refresh dei dot (es. quando arriva una notifica) */
    fun refreshNotificationBadges() {
        // Non posso enumerare le icone direttamente, ma faccio invalidate ricorsivo
        invalidateRecursive(this)
    }

    private fun invalidateRecursive(view: View) {
        view.invalidate()
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) invalidateRecursive(view.getChildAt(i))
        }
    }
}
