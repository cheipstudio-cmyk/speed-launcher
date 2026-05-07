package org.cheipstudio.speedlauncher.ui

import android.app.AlertDialog
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

/**
 * v9: long-press su widget piazzato → conferma rimozione.
 * long-press su slot vuoto → picker widget.
 */
class WidgetSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var hostController: WidgetHostController? = null
    private var currentWidgetView: View? = null
    private var currentWidgetId: Int = -1
    private val placeholder: LinearLayout

    init {
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
            handleLongPress()
            true
        }
        isLongClickable = true
    }

    fun setHostController(controller: WidgetHostController) {
        hostController = controller
    }

    private fun handleLongPress() {
        if (currentWidgetView == null) {
            // Slot vuoto: picker
            val controller = hostController ?: return
            controller.pickAndAddWidget { view ->
                view ?: return@pickAndAddWidget
                currentWidgetView = view
                currentWidgetId = controller.lastWidgetId
                removeAllViews()
                addView(view)
            }
        } else {
            // Widget presente: chiedi rimozione
            AlertDialog.Builder(context)
                .setTitle(R.string.widget_remove_title)
                .setMessage(R.string.widget_remove_message)
                .setPositiveButton(R.string.widget_remove_confirm) { _, _ ->
                    removeWidget()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun removeWidget() {
        val controller = hostController ?: return
        if (currentWidgetId != -1) {
            controller.deleteWidget(currentWidgetId)
            currentWidgetId = -1
        }
        currentWidgetView = null
        removeAllViews()
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}
