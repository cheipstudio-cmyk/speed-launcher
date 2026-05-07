package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.HomeItem

/**
 * v16: BottomSheet che mostra le app dentro una folder.
 * - Tap app = lancia
 * - Long-press app = rimuovi dalla folder (riapre come app singola)
 * - Edit del nome in alto
 */
object FolderSheet {

    fun show(
        context: Context,
        folder: HomeItem,
        onLaunch: (AppInfo) -> Unit,
        onRename: (String) -> Unit,
        onRemoveFromFolder: (AppInfo) -> Unit,
        onDeleteFolder: () -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (24 * density).toInt())
        }

        // handle
        val handle = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_drag_handle)
            val lp = LinearLayout.LayoutParams((32 * density).toInt(), (4 * density).toInt())
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(handle)

        // titolo editabile
        val nameInput = EditText(context).apply {
            setText(folder.name)
            textSize = 22f
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            background = null
            setHintTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            hint = context.getString(R.string.folder_name_hint)
            setSingleLine(true)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (16 * density).toInt()
            layoutParams = lp
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onRename(s?.toString() ?: "") }
            })
        }
        container.addView(nameInput)

        // griglia di app
        val apps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val byKey = apps.associateBy { it.key }
        val folderApps = folder.folderApps.mapNotNull { byKey[it] }

        val cols = 4
        val grid = GridLayout(context).apply {
            columnCount = cols
            useDefaultMargins = false
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        for (app in folderApps) {
            val cell = buildAppCell(context, app, onLaunch, onRemoveFromFolder)
            grid.addView(cell)
        }
        container.addView(grid)

        // bottone elimina folder
        val deleteBtn = TextView(context).apply {
            text = context.getString(R.string.folder_delete)
            setTextColor(Color.parseColor("#FF6464"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (16 * density).toInt()
            layoutParams = lp
        }
        container.addView(deleteBtn)

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(container)
        deleteBtn.setOnClickListener { onDeleteFolder(); dialog.dismiss() }
        dialog.show()
    }

    private fun buildAppCell(
        context: Context,
        app: AppInfo,
        onLaunch: (AppInfo) -> Unit,
        onRemoveFromFolder: (AppInfo) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true; isFocusable = true
            background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
        }
        val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
        lp.width = 0; lp.height = (90 * density).toInt()
        cell.layoutParams = lp

        val icon = ImageView(context).apply {
            setImageDrawable(app.icon)
            val s = (44 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        cell.addView(icon)

        val label = TextView(context).apply {
            text = app.label
            setTextColor(resolveAttr(context, com.google.android.material.R.attr.colorOnSurface))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = (4 * density).toInt()
            layoutParams = tlp
        }
        cell.addView(label)

        cell.setOnClickListener { onLaunch(app) }
        cell.setOnLongClickListener {
            onRemoveFromFolder(app)
            true
        }
        return cell
    }

    private fun resolveAttr(context: Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
