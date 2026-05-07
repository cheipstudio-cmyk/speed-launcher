package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.widgets.WidgetHostController

class WidgetSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var hostController: WidgetHostController? = null
    private var currentWidgetView: View? = null
    private val placeholder: LinearLayout

    init {
        // Placeholder visivo: "Tieni premuto per aggiungere un widget"
        placeholder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = androidx.core.content.ContextCompat.getDrawable(
                context, R.drawable.bg_widget_placeholder
            )
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_widgets)
            setColorFilter(Color.parseColor("#88FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                (28 * resources.displayMetrics.density).toInt(),
                (28 * resources.displayMetrics.density).toInt()
            )
        }
        placeholder.addView(icon)

        val text = TextView(context).apply {
            setText(R.string.widget_placeholder_hint)
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (6 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        placeholder.addView(text)

        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setOnLongClickListener {
            if (currentWidgetView == null) {
                handleLongPress()
                true
            } else {
                false
            }
        }
        isLongClickable = true
    }

    fun setHostController(controller: WidgetHostController) {
        hostController = controller
    }

    private fun handleLongPress() {
        val controller = hostController ?: return
        controller.pickAndAddWidget { view ->
            view ?: return@pickAndAddWidget
            currentWidgetView = view
            removeAllViews()
            addView(view)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}
