package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * Tutorial overlay full-screen che appare al primo avvio.
 * 3 step + bottone "Avanti / Fatto".
 */
class TutorialOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val card: LinearLayout
    private val titleView: TextView
    private val descView: TextView
    private val iconView: ImageView
    private val nextBtn: Button
    private val skipBtn: Button
    private val stepIndicator: TextView

    private var step = 0
    private val steps = listOf(
        Triple(
            R.string.tutorial_swipe_title,
            R.string.tutorial_swipe_desc,
            R.drawable.ic_swipe_up
        ),
        Triple(
            R.string.tutorial_longpress_icon_title,
            R.string.tutorial_longpress_icon_desc,
            R.drawable.ic_pin
        ),
        Triple(
            R.string.tutorial_longpress_home_title,
            R.string.tutorial_longpress_home_desc,
            R.drawable.ic_settings
        )
    )

    init {
        // Sfondo semi-trasparente
        setBackgroundColor(Color.parseColor("#CC000000"))
        isClickable = true
        isFocusable = true

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = androidx.core.content.ContextCompat.getDrawable(
                context, R.drawable.bg_tutorial_card
            )
            val pad = (32 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                val margin = (32 * resources.displayMetrics.density).toInt()
                leftMargin = margin
                rightMargin = margin
            }
            layoutParams = lp
        }

        iconView = ImageView(context).apply {
            val size = (72 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setColorFilter(Color.WHITE)
        }
        card.addView(iconView)

        titleView = TextView(context).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        card.addView(titleView)

        descView = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (24 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        card.addView(descView)

        stepIndicator = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        card.addView(stepIndicator)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        skipBtn = Button(context).apply {
            text = context.getString(R.string.tutorial_skip)
            setTextColor(Color.parseColor("#AAFFFFFF"))
            setBackgroundColor(Color.TRANSPARENT)
        }
        nextBtn = Button(context).apply {
            text = context.getString(R.string.tutorial_next)
            setTextColor(Color.WHITE)
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_tutorial_btn)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        btnRow.addView(skipBtn)
        btnRow.addView(nextBtn)
        card.addView(btnRow)

        addView(card)

        skipBtn.setOnClickListener { dismiss() }
        nextBtn.setOnClickListener {
            step++
            if (step >= steps.size) dismiss() else updateStep()
        }
        updateStep()
    }

    private fun updateStep() {
        val (titleRes, descRes, iconRes) = steps[step]
        titleView.setText(titleRes)
        descView.setText(descRes)
        iconView.setImageResource(iconRes)
        stepIndicator.text = "${step + 1} / ${steps.size}"
        nextBtn.text = if (step == steps.size - 1)
            context.getString(R.string.tutorial_done)
        else
            context.getString(R.string.tutorial_next)
    }

    private fun dismiss() {
        SpeedApp.instance.settingsRepository.markTutorialSeen()
        animate().alpha(0f).setDuration(200).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
        }.start()
    }
}
