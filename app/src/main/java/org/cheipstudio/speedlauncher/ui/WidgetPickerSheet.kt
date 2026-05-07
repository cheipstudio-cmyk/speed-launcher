package org.cheipstudio.speedlauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R

/**
 * Picker custom: lista pulita, filtro per dimensione slot, search.
 */
class WidgetPickerSheet : BottomSheetDialogFragment() {

    var onWidgetSelected: ((AppWidgetProviderInfo) -> Unit)? = null

    var slotWidthPx: Int = 0
    var slotHeightPx: Int = 0

    private lateinit var adapter: WidgetAdapter
    private var allItems: List<AppWidgetProviderInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_widget_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.widgetRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = WidgetAdapter { info ->
            onWidgetSelected?.invoke(info)
            dismissAllowingStateLoss()
        }
        recycler.adapter = adapter

        view.findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        loadWidgets()
    }

    private fun loadWidgets() {
        val ctx = requireContext()
        val manager = AppWidgetManager.getInstance(ctx)
        val all = manager.installedProviders
        // Filtro: minWidth/minHeight devono entrare nello slot disponibile
        val filtered = all.filter { info ->
            val minW = info.minWidth.coerceAtLeast(1)
            val minH = info.minHeight.coerceAtLeast(1)
            (slotWidthPx == 0 || minW <= slotWidthPx + 32) &&
                    (slotHeightPx == 0 || minH <= slotHeightPx + 32)
        }.sortedBy { it.loadLabel(ctx.packageManager).lowercase() }

        allItems = filtered
        adapter.submit(filtered)
        view?.findViewById<TextView>(R.id.emptyText)?.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val pm = requireContext().packageManager
        val filtered = if (q.isEmpty()) allItems
        else allItems.filter { it.loadLabel(pm).lowercase().contains(q) }
        adapter.submit(filtered)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            }
        }
        return dialog
    }

    private class WidgetAdapter(val onClick: (AppWidgetProviderInfo) -> Unit)
        : RecyclerView.Adapter<WidgetAdapter.VH>() {

        private val items = mutableListOf<AppWidgetProviderInfo>()
        fun submit(list: List<AppWidgetProviderInfo>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_picker, parent, false)
            return VH(v)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
            holder.itemView.setOnClickListener { onClick(items[position]) }
        }
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val icon = v.findViewById<ImageView>(R.id.widgetIcon)
            private val name = v.findViewById<TextView>(R.id.widgetName)
            private val app = v.findViewById<TextView>(R.id.widgetApp)
            private val size = v.findViewById<TextView>(R.id.widgetSize)
            fun bind(info: AppWidgetProviderInfo) {
                val ctx = itemView.context
                val pm = ctx.packageManager
                name.text = info.loadLabel(pm)
                try {
                    val ai = pm.getApplicationInfo(info.provider.packageName, 0)
                    app.text = pm.getApplicationLabel(ai)
                    val preview = info.loadPreviewImage(ctx, 0)
                    icon.setImageDrawable(preview ?: pm.getApplicationIcon(ai))
                } catch (_: Throwable) {
                    app.text = info.provider.packageName
                    icon.setImageDrawable(info.loadPreviewImage(ctx, 0))
                }
                val cellsW = (info.minWidth / 70).coerceAtLeast(1)
                val cellsH = (info.minHeight / 70).coerceAtLeast(1)
                size.text = "${cellsW}×${cellsH}"
            }
        }
    }

    companion object {
        fun newInstance(slotWidthPx: Int, slotHeightPx: Int): WidgetPickerSheet {
            return WidgetPickerSheet().apply {
                this.slotWidthPx = slotWidthPx
                this.slotHeightPx = slotHeightPx
            }
        }
    }
}
