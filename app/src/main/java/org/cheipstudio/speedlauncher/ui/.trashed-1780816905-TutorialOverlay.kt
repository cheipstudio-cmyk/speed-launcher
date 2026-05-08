package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp

/**
 * v46: tutorial ridisegnato.
 * - Container con dimensioni FISSE: niente più jump al next
 * - Cross-fade tra step (smooth)
 * - 6 step con copy esaustivo
 * - Progress bar in cima
 * - Icona in cerchio gradient con pulse
 */
class TutorialOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backdrop: View
    private val card: LinearLayout
    private val titleView: TextView
    private val descView: TextView
    private val iconCircle: FrameLayout
    private val iconView: ImageView
    private val progressBar: View
    private val progressFill: View
    private val nextBtn: MaterialButton
    private val skipBtn: TextView
    private val setDefaultBtn: MaterialButton
    private val contentInner: LinearLayout

    private var step = 0
    private data class Step(val titleRes: Int, val descRes: Int, val iconRes: Int, val accent: Int)
    private val steps = listOf(
        Step(R.string.tutorial_welcome_title, R.string.tutorial_welcome_desc,
             R.drawable.ic_star_outline, 0xFFFFB4A8.toInt()),
        Step(R.string.tutorial_swipe_title, R.string.tutorial_swipe_desc,
             R.drawable.ic_gesture, 0xFF89B4FA.toInt()),
        Step(R.string.tutorial_longpress_icon_title, R.string.tutorial_longpress_icon_desc,
             R.drawable.ic_apps_outline, 0xFFA6E3A1.toInt()),
        Step(R.string.tutorial_home_long_press_title, R.string.tutorial_home_long_press_desc,
             R.drawable.ic_home_outline, 0xFFCBA6F7.toInt()),
        Step(R.string.tutorial_settings_title, R.string.tutorial_settings_desc,
             R.drawable.ic_tune, 0xFFFFD68C.toInt()),
        Step(R.string.tutorial_widget_title, R.string.tutorial_widget_desc,
             R.drawable.ic_widget, 0xFF89B4FA.toInt()),
        Step(R.string.tutorial_default_title, R.string.tutorial_default_desc,
             R.drawable.ic_home_outline, 0xFFA6E3A1.toInt())
    )

    init {
        val density = resources.displayMetrics.density

        // Backdrop
        backdrop = View(context).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        }
        addView(backdrop)

        // Card outer (fissa altezza grande così non salta tra step)
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32 * density
                setColor(resolveAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            }
            setPadding(
                (28 * density).toInt(), (28 * density).toInt(),
                (28 * density).toInt(), (24 * density).toInt()
            )
            val lp = LayoutParams(
                (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }

        // Progress bar in alto
        val progressContainer = FrameLayout(context).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (4 * density).toInt())
            lp.bottomMargin = (24 * density).toInt()
            layoutParams = lp
        }
        progressBar = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2 * density
                setColor(resolveAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        progressContainer.addView(progressBar)
        progressFill = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2 * density
                setColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary))
            }
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        progressContainer.addView(progressFill)
        card.addView(progressContainer)

        // Inner content (icona + titolo + desc) — quello che cross-fade-ia
        contentInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // ALTEZZA FISSA per evitare salti tra step di lunghezze diverse
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (340 * density).toInt()
            )
            layoutParams = lp
        }

        // Cerchio icona
        iconCircle = FrameLayout(context).apply {
            val lp = LinearLayout.LayoutParams((84 * density).toInt(), (84 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (24 * density).toInt()
            layoutParams = lp
        }
        iconView = ImageView(context).apply {
            val lp = FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
            lp.gravity = Gravity.CENTER
            layoutParams = lp
            setColorFilter(0xFF1B1B1F.toInt())
        }
        iconCircle.addView(iconView)
        contentInner.addView(iconCircle)

        // Titolo
        titleView = TextView(context).apply {
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * density).toInt()
            layoutParams = lp
        }
        contentInner.addView(titleView)

        // Descrizione
        descView = TextView(context).apply {
            textSize = 15f
            setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        contentInner.addView(descView)

        card.addView(contentInner)

        // Bottoni footer
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (8 * density).toInt()
            layoutParams = lp
        }

        skipBtn = TextView(context).apply {
            text = context.getString(R.string.tutorial_skip)
            setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 14f
            setPadding(
                (8 * density).toInt(), (12 * density).toInt(),
                (8 * density).toInt(), (12 * density).toInt()
            )
            isClickable = true; isFocusable = true
            // Selector ripple via theme attr
            val tv2 = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv2, true)
            setBackgroundResource(tv2.resourceId)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setOnClickListener { dismissTutorial() }
        }
        buttonsRow.addView(skipBtn)

        // Spacer
        buttonsRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        setDefaultBtn = MaterialButton(context).apply {
            text = context.getString(R.string.tutorial_set_default)
            visibility = GONE
            cornerRadius = (24 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (8 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Throwable) {}
            }
        }
        buttonsRow.addView(setDefaultBtn)

        nextBtn = MaterialButton(context).apply {
            text = context.getString(R.string.tutorial_next)
            cornerRadius = (24 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setOnClickListener { nextStep() }
        }
        buttonsRow.addView(nextBtn)

        card.addView(buttonsRow)

        addView(card)

        // Animazione di entrata
        card.alpha = 0f
        card.scaleX = 0.92f; card.scaleY = 0.92f
        card.translationY = 32 * density
        card.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(380)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()

        showStep(0, animate = false)
    }

    private fun resolveAttrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun nextStep() {
        if (step < steps.size - 1) {
            step++
            showStep(step, animate = true)
        } else {
            dismissTutorial()
        }
    }

    private fun showStep(idx: Int, animate: Boolean) {
        val s = steps[idx]
        val density = resources.displayMetrics.density

        val applyContent = {
            titleView.setText(s.titleRes)
            descView.setText(s.descRes)
            iconView.setImageResource(s.iconRes)
            // Cerchio gradient con accent del step
            iconCircle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(s.accent)
            }
            // Bottone next: se ultimo step → "Inizia"
            nextBtn.text = context.getString(
                if (idx == steps.size - 1) R.string.tutorial_done else R.string.tutorial_next
            )
            // Set default button: solo all'ultimo step
            setDefaultBtn.visibility = if (idx == steps.size - 1) View.VISIBLE else View.GONE

            // Progress bar animation
            val cardWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt() -
                            (28 * density).toInt() * 2
            val targetWidth = (cardWidth * (idx + 1).toFloat() / steps.size).toInt()
            val animator = android.animation.ValueAnimator.ofInt(progressFill.width, targetWidth)
            animator.duration = 450
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener {
                progressFill.layoutParams = (progressFill.layoutParams as FrameLayout.LayoutParams).apply {
                    width = it.animatedValue as Int
                }
                progressFill.requestLayout()
            }
            animator.start()
        }

        if (!animate) {
            applyContent()
            // Pulse iniziale icona
            iconCircle.scaleX = 0f; iconCircle.scaleY = 0f
            iconCircle.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(OvershootInterpolator(1.4f)).setStartDelay(150).start()
            return
        }

        // Cross-fade: fade out content, swap, fade in
        contentInner.animate()
            .alpha(0f).translationY(-12 * density)
            .setDuration(170)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                applyContent()
                contentInner.translationY = 12 * density
                contentInner.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                // Pulse icona ad ogni step
                iconCircle.scaleX = 0.7f; iconCircle.scaleY = 0.7f
                iconCircle.animate().scaleX(1f).scaleY(1f).setDuration(380)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
            .start()
    }

    private fun dismissTutorial() {
        SpeedApp.instance.settingsRepository.markTutorialSeen()
        animate().alpha(0f).setDuration(220).withEndAction {
            (parent as? android.view.ViewGroup)?.removeView(this)
        }.start()
    }

    companion object {
        fun showIfNeeded(rootView: android.view.ViewGroup) {
            val seen = SpeedApp.instance.settingsRepository.tutorialSeen.value == true
            if (seen) return
            val overlay = TutorialOverlay(rootView.context)
            rootView.addView(overlay,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        fun showAlways(rootView: android.view.ViewGroup) {
            val overlay = TutorialOverlay(rootView.context)
            rootView.addView(overlay,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }
}
