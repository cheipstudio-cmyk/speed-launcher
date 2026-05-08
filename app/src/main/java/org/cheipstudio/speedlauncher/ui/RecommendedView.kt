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

    private val density = resources.displayMetrics.density
    private val card: MaterialCardView
    private val row: LinearLayout

    init {
        // Card wrapper
        card = MaterialCardView(context).apply {
            radius = 28 * density
            cardElevation = 0f
            // v41: sfondo appena percepibile, no stacco grigio
            setCardBackgroundColor(themedBgColor())
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                (8 * density).toInt(),
                (10 * density).toInt(),
                (8 * density).toInt(),
                (10 * density).toInt()
            )
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
        // slideOffset: -1 (hidden) → 0 (collapsed/peek) → 1 (expanded)
        val s = slideOffset.coerceIn(-1f, 1f)
        // Parallax progress: visibile da 0 (peek) a 1 (expanded)
        // Curva non lineare per dare effetto "scorrimento" più evidente
        val raw = ((s).coerceIn(0f, 1f))
        val visibleProgress = raw  // 0..1 lineare
        // Alpha: ben visibile da subito, ma fade fino a 0.3 quando in peek
        alpha = 0.3f + 0.7f * visibleProgress
        // Parallax: scivola verso il basso di +56dp quando in peek (più drammatico)
        translationY = (1f - visibleProgress) * (56f * density)
        // Scala più marcata per profondità
        val sc = 0.85f + 0.15f * visibleProgress
        scaleX = sc; scaleY = sc
    }

    fun refresh(scope: String = "home") {
        val tracker = SpeedApp.instance.usageTracker
        val all = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val hidden = SpeedApp.instance.settingsRepository.hiddenApps.value ?: emptySet<String>()
        val available = all.filter { !hidden.contains(it.key) }
        val byKey = available.associateBy { it.key }
        val countToShow = SpeedApp.instance.settingsRepository.recommendedCount.value ?: 5
        val topKeys = tracker.getTopApps(byKey.keys, topN = countToShow)
        val topApps = topKeys.mapNotNull { byKey[it] }

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
        card.visibility = if (showCard) VISIBLE else GONE
        if (!showCard) {
            // se nel drawer non vogliamo card, nascondiamo card e mettiamo row direttamente in this
            // (manteniamo struttura semplice: in drawer la row viene mostrata senza card di sfondo
            // tramite cardBackgroundColor trasparente)
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
            val s = (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
        val icon = ImageView(context).apply {
            setImageDrawable(IconShaper.shape(app.icon, shape))
            val s = (44 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
        }
        iconWrap.addView(icon)
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
        }
        cell.addView(label)

        cell.setOnClickListener { onAppClick?.invoke(app) }
        cell.setOnLongClickListener { onAppLongPress?.invoke(app); true }
        return cell
    }
}
