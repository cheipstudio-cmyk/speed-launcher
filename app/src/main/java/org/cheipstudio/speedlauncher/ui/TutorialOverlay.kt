package org.cheipstudio.speedlauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v10: tutorial con design migliorato:
 * - Background con gradient blur
 * - Card più grande, padding generoso
 * - Icona animata in entrata
 * - Indicatore step a pallini
 * - Titolo + descrizione + CTA
 * - Animazioni di transizione tra step
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
    private val dotsContainer: LinearLayout

    private var step = 0
    private val steps = listOf(
        Triple(R.string.tutorial_swipe_title, R.string.tutorial_swipe_desc, R.drawable.ic_swipe_up),
        Triple(R.string.tutorial_longpress_icon_title, R.string.tutorial_longpress_icon_desc, R.drawable.ic_pin),
        Triple(R.string.tutorial_longpress_home_title, R.string.tutorial_longpress_home_desc, R.drawable.ic_settings),
        Triple(R.string.tutorial_pages_title, R.string.tutorial_pages_desc, R.drawable.ic_pages)
    )

    init {
        // Background gradient + blur opacity
        background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_overlay)
        isClickable = true
        isFocusable = true

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
                leftMargin = margin
                rightMargin = margin
            }
            layoutParams = lp
        }

        // Icona dentro un cerchio gradient
        val iconWrapper = FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_icon)
            val size = (88 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (20 * density).toInt()
            }
        }
        iconView = ImageView(context).apply {
            val size = (40 * density).toInt()
            layoutParams = LayoutParams(size, size, Gravity.CENTER)
            setColorFilter(Color.WHITE)
        }
        iconWrapper.addView(iconView)
        card.addView(iconWrapper)

        titleView = TextView(context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * density).toInt()
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

        // Dots
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

        // Bottoni
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        skipBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_skip)
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            isClickable = true
            isFocusable = true
        }

        nextBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_next)
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_tutorial_btn)
            setPadding((28 * density).toInt(), (12 * density).toInt(), (28 * density).toInt(), (12 * density).toInt())
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (12 * density).toInt()
            layoutParams = lp
        }

        // Spacer
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

        // Animazione iniziale
        card.alpha = 0f
        card.translationY = 60f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
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
            iconView.alpha = v
            titleView.alpha = v
            descView.alpha = v
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                updateStep()
                rebuildDots()
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 250
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        iconView.alpha = v
                        titleView.alpha = v
                        descView.alpha = v
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
        nextBtn.text = if (step == steps.size - 1)
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
