package org.cheipstudio.speedlauncher.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import org.cheipstudio.speedlauncher.R

/**
 * v240: Modal azioni per un widget specifico (multi-widget per pagina).
 * Presenta: Rimuovi widget. (In v2.4.x successive: Sposta a pagina, Personalizza dimensioni)
 */
class WidgetActionsSheet : BottomSheetDialogFragment() {

    var onRemove: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val uuid = arguments?.getString(ARG_UUID) ?: ""

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_modal_sheet)
            setPadding(0, (8 * d).toInt(), 0, (24 * d).toInt())
        }

        // Drag handle
        root.addView(View(ctx).apply {
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((40 * d).toInt(), (4 * d).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (8 * d).toInt()
            lp.bottomMargin = (16 * d).toInt()
            layoutParams = lp
        })

        // Title
        root.addView(TextView(ctx).apply {
            text = getString(R.string.widget_actions_title)
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * d).toInt()
            lp.rightMargin = (24 * d).toInt()
            lp.bottomMargin = (16 * d).toInt()
            layoutParams = lp
        })

        // Hint
        root.addView(TextView(ctx).apply {
            text = getString(R.string.widget_actions_hint)
            textSize = 14f
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * d).toInt()
            lp.rightMargin = (24 * d).toInt()
            lp.bottomMargin = (24 * d).toInt()
            layoutParams = lp
        })

        // Rimuovi
        val removeBtn = MaterialButton(ctx).apply {
            text = getString(R.string.widget_remove_action)
            cornerRadius = (32 * d).toInt()
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorErrorContainer))
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnErrorContainer))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            textSize = 15f
            isAllCaps = false
            insetTop = 0; insetBottom = 0
            val padV = (14 * d).toInt()
            setPadding(padV, padV, padV, padV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (16 * d).toInt()
            lp.rightMargin = (16 * d).toInt()
            layoutParams = lp
            setOnClickListener {
                onRemove?.invoke(uuid)
                dismiss()
            }
        }
        root.addView(removeBtn)

        return root
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    companion object {
        private const val ARG_UUID = "uuid"
        private const val ARG_PAGE = "pageIndex"

        fun newInstance(uuid: String, pageIndex: Int): WidgetActionsSheet {
            return WidgetActionsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_UUID, uuid)
                    putInt(ARG_PAGE, pageIndex)
                }
            }
        }
    }
}
