package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.cheipstudio.speedlauncher.widgets.WidgetHostController

class WidgetSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var hostController: WidgetHostController? = null
    private var currentWidgetView: View? = null

    init {
        setOnLongClickListener {
            if (currentWidgetView == null) {
                handleLongPress()
                true
            } else {
                false
            }
        }
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
