package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.SettingsRepository

/**
 * v30: riga "Raccomandate" — top 5 app per uso recente con decay.
 * Mostra header "Raccomandate" + 5 icone orizzontali.
 * Tap → lancio app + record. Long press → menu app.
 */
class RecommendedView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongPress: ((AppInfo) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val header: TextView
    private val row: LinearLayout

    init {
        orientation = VERTICAL
        setPadding(
            (16 * density).toInt(), (8 * density).toInt(),
            (16 * density).toInt(), (12 * density).toInt()
        )

        header = TextView(context).apply {
            text = context.getString(R.string.recommended_header)
            textSize = 12f
            setTextColor(Color.WHITE)
            setShadowLayer(2f, 0f, 1f, Color.argb(160, 0, 0, 0))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.06f
            isAllCaps = true
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (8 * density).toInt()
            lp.leftMargin = (4 * density).toInt()
            layoutParams = lp
        }
        addView(header)

        row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(row)
    }

    /**
     * Aggiorna la riga con le top app correnti.
     * @param hostScope se "drawer" applica colori scuri (su sfondo surface), altrimenti white per home wallpaper.
     */
    fun refresh(scope: String = "home") {
        val tracker = SpeedApp.instance.usageTracker
        val all = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val hidden = SpeedApp.instance.settingsRepository.hiddenApps.value ?: emptySet<String>()
        val available = all.filter { !hidden.contains(it.key) }
        val byKey = available.associateBy { it.key }
        val topKeys = tracker.getTopApps(byKey.keys, topN = 5)
        val topApps = topKeys.mapNotNull { byKey[it] }

        // Se non c'è ancora storico, mostra niente o le prime 5 app a caso? Mostra niente.
        if (topApps.isEmpty()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        // Adatta colori in base allo scope
        val labelColor: Int
        val shadow: Boolean
        when (scope) {
            "drawer" -> {
                val tv = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
                labelColor = tv.data
                shadow = false
            }
            else -> {
                labelColor = Color.WHITE
                shadow = true
            }
        }
        header.setTextColor(labelColor)
        if (!shadow) header.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        else header.setShadowLayer(2f, 0f, 1f, Color.argb(160, 0, 0, 0))

        row.removeAllViews()
        val shape = SpeedApp.instance.settingsRepository.iconShape.value ?: SettingsRepository.SHAPE_ORIGINAL
        for (app in topApps) {
            row.addView(buildCell(app, labelColor, shadow, shape))
        }
    }

    private fun buildCell(app: AppInfo, labelColor: Int, shadow: Boolean, shape: String): View {
        val cell = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val pad = (4 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true; isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_app_icon_ripple_themed)
        }
        val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        cell.layoutParams = lp

        val iconWrap = FrameLayout(context).apply {
            val s = (52 * density).toInt()
            layoutParams = LayoutParams(s, s)
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
            val tlp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            tlp.topMargin = (4 * density).toInt()
            layoutParams = tlp
        }
        cell.addView(label)

        cell.setOnClickListener { onAppClick?.invoke(app) }
        cell.setOnLongClickListener { onAppLongPress?.invoke(app); true }
        return cell
    }
}
