package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.widgets.WidgetHostController

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
            showCustomPicker()
        } else {
            WidgetRemoveSheet.show(context) { removeWidget() }
        }
    }

    private fun showCustomPicker() {
        val activity = context as? FragmentActivity ?: return
        val sheet = WidgetPickerSheet.newInstance(width, height)
        sheet.onWidgetSelected = { info -> bindAndAdd(info) }
        sheet.show(activity.supportFragmentManager, "widget_picker")
    }

    private fun bindAndAdd(info: AppWidgetProviderInfo) {
        val controller = hostController ?: return
        val activity = context as? Activity ?: return
        val appWidgetId = controller.host.allocateAppWidgetId()
        val canBind = controller.appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (!canBind) {
            // Richiedi all'utente il permesso di bind
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            controller.pendingBindWidget = info
            controller.pendingBindAppWidgetId = appWidgetId
            controller.pendingPlaceCallback = { v ->
                v?.let {
                    currentWidgetView = it
                    currentWidgetId = controller.lastWidgetId
                    removeAllViews()
                    addView(it)
                }
            }
            activity.startActivityForResult(bindIntent, WidgetHostController.REQ_BIND)
            return
        }
        // Già autorizzato, configura se serve
        if (info.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            controller.pendingPlaceCallback = { v ->
                v?.let {
                    currentWidgetView = it
                    currentWidgetId = controller.lastWidgetId
                    removeAllViews()
                    addView(it)
                }
            }
            activity.startActivityForResult(configIntent, WidgetHostController.REQ_CONFIGURE)
        } else {
            val view = controller.createView(appWidgetId, info)
            view.setAppWidget(appWidgetId, info)
            controller.markLastWidget(appWidgetId)
            currentWidgetView = view
            currentWidgetId = appWidgetId
            removeAllViews()
            addView(view)
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
