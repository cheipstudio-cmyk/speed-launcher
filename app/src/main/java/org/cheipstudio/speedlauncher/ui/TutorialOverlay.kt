package org.cheipstudio.speedlauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v18: tutorial più espressivo:
 * - Card con icona dentro un cerchio gradient
 * - Animazione overshoot per l'icona (bounce-in)
 * - 5 step (l'ultimo con bottone "Imposta come predefinito")
 * - Pulsante "Avanti" con effetto press
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
    private val nextBtn: TextView
    private val skipBtn: TextView
    private val setDefaultBtn: TextView
    private val dotsContainer: LinearLayout

    private var step = 0
    private val steps = listOf(
        Triple(R.string.tutorial_swipe_title, R.string.tutorial_swipe_desc, R.drawable.ic_swipe_up),
        Triple(R.string.tutorial_longpress_icon_title, R.string.tutorial_longpress_icon_desc, R.drawable.ic_pin),
        Triple(R.string.tutorial_settings_title, R.string.tutorial_settings_desc, R.drawable.ic_settings),
        Triple(R.string.tutorial_pages_title, R.string.tutorial_pages_desc, R.drawable.ic_pages),
        Triple(R.string.tutorial_default_title, R.string.tutorial_default_desc, R.drawable.ic_widgets)
    )

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_overlay)
        isClickable = true; isFocusable = true
        val density = resources.displayMetrics.density

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_card)
            val pad = (28 * density).toInt()
            setPadding(pad, (32 * density).toInt(), pad, (24 * density).toInt())
            elevation = 16 * density
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                val margin = (28 * density).toInt()
                leftMargin = margin; rightMargin = margin
            }
            layoutParams = lp
        }

        val iconWrapper = FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_icon)
            val size = (96 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (24 * density).toInt()
            }
        }
        iconView = ImageView(context).apply {
            val size = (44 * density).toInt()
            layoutParams = LayoutParams(size, size, Gravity.CENTER)
            setColorFilter(Color.WHITE)
        }
        iconWrapper.addView(iconView)
        card.addView(iconWrapper)

        titleView = TextView(context).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * density).toInt()
            layoutParams = lp
        }
        card.addView(titleView)

        descView = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            lineHeight = (22 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (24 * density).toInt()
            val hpad = (8 * density).toInt()
            setPadding(hpad, 0, hpad, 0)
            layoutParams = lp
        }
        card.addView(descView)

        // bottone "Imposta come predefinito" (visibile solo all'ultimo step)
        setDefaultBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_set_default_btn)
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_set_default_btn)
            setPadding((24 * density).toInt(), (14 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
            isClickable = true; isFocusable = true
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {}
            }
        }
        card.addView(setDefaultBtn)

        dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (24 * density).toInt()
            layoutParams = lp
        }
        card.addView(dotsContainer)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        skipBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_skip)
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            isClickable = true; isFocusable = true
        }

        nextBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_next)
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_btn)
            setPadding((28 * density).toInt(), (12 * density).toInt(), (28 * density).toInt(), (12 * density).toInt())
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (12 * density).toInt()
            layoutParams = lp
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnRow.addView(skipBtn)
        btnRow.addView(spacer)
        btnRow.addView(nextBtn)
        card.addView(btnRow)

        addView(card)

        skipBtn.setOnClickListener { dismiss() }
        nextBtn.setOnClickListener {
            step++
            if (step >= steps.size) dismiss() else animateStepChange()
        }
        rebuildDots()
        updateStep()

        // entrata espressiva con overshoot
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.translationY = 60f
        card.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(420)
            .setInterpolator(OvershootInterpolator(0.6f)).start()
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        for (i in steps.indices) {
            val dot = View(context).apply {
                background = ContextCompat.getDrawable(
                    context,
                    if (i == step) R.drawable.bg_tutorial_dot_active
                    else R.drawable.bg_tutorial_dot_inactive
                )
                val size = (8 * density).toInt()
                val active = (24 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    if (i == step) active else size, size
                ).apply {
                    leftMargin = (4 * density).toInt()
                    rightMargin = (4 * density).toInt()
                }
            }
            dotsContainer.addView(dot)
        }
    }

    private fun animateStepChange() {
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.duration = 150
        anim.addUpdateListener {
            val v = it.animatedValue as Float
            iconView.alpha = v; titleView.alpha = v; descView.alpha = v
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                updateStep(); rebuildDots()
                // bounce-in dell'icona
                iconView.scaleX = 0.5f; iconView.scaleY = 0.5f
                iconView.animate().scaleX(1f).scaleY(1f)
                    .setDuration(380)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 250
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        iconView.alpha = v; titleView.alpha = v; descView.alpha = v
                    }
                    start()
                }
            }
        })
        anim.start()
    }

    private fun updateStep() {
        val (titleRes, descRes, iconRes) = steps[step]
        titleView.setText(titleRes)
        descView.setText(descRes)
        iconView.setImageResource(iconRes)
        // ultimo step: mostra bottone "Imposta come predefinito"
        val isLast = step == steps.size - 1
        setDefaultBtn.visibility = if (isLast) View.VISIBLE else View.GONE
        nextBtn.text = if (isLast)
            context.getString(R.string.tutorial_done)
        else
            context.getString(R.string.tutorial_next)
    }

    private fun dismiss() {
        SpeedApp.instance.settingsRepository.markTutorialSeen()
        animate().alpha(0f).setDuration(220).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
        }.start()
    }
}
