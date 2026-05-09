package org.cheipstudio.speedlauncher

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.cheipstudio.speedlauncher.data.AppInfo

/**
 * v145: Activity dedicata per scegliere e ordinare le app raccomandate manuali.
 * Drag&Drop nativo con ItemTouchHelper. Niente più frecce confuse.
 */
class RecommendedPickerActivity : AppCompatActivity() {

    private val settings get() = SpeedApp.instance.settingsRepository
    private val ordered = mutableListOf<String>()
    private lateinit var available: List<AppInfo>
    private lateinit var byKey: Map<String, AppInfo>
    private var countNeeded = 5

    private lateinit var selectedAdapter: SelectedAdapter
    private lateinit var availableAdapter: AvailableAdapter
    private lateinit var counterLabel: TextView
    private lateinit var availableHeader: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)
        window.navigationBarColor = resolveAttr(com.google.android.material.R.attr.colorSurface)

        val density = resources.displayMetrics.density

        val apps = SpeedApp.instance.appRepository.apps.value
            ?.filter { it.packageName != packageName }
            ?: emptyList<AppInfo>()
        if (apps.isEmpty()) {
            // App non ancora caricate: aspetto un attimo
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                recreate()
            }, 300)
        }
        val hidden = settings.hiddenApps.value ?: emptySet<String>()
        available = apps.filter { !hidden.contains(it.key) }.sortedBy { it.label.lowercase() }
        byKey = available.associateBy { it.key }
        countNeeded = settings.recommendedCount.value ?: 5

        val current = (settings.recommendedManualOrder.value ?: emptyList<String>()).toMutableList()
        val currentSet = settings.recommendedManualApps.value ?: emptySet<String>()
        for (k in currentSet) if (k !in current) current.add(k)
        current.retainAll { k -> byKey.containsKey(k) }
        ordered.addAll(current)

        // ROOT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorSurface))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
            title = getString(R.string.rec_picker_title)
        }
        root.addView(toolbar)

        // Counter sotto toolbar
        counterLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val pad = (16 * density).toInt()
            setPadding(pad, (4 * density).toInt(), pad, (8 * density).toInt())
        }
        root.addView(counterLabel)

        // Header sezione "Selezionate"
        val selectedHeader = sectionHeader(getString(R.string.rec_picker_selected_header))
        selectedHeader.setPadding(
            (16 * density).toInt(),
            (8 * density).toInt(),
            (16 * density).toInt(),
            (8 * density).toInt()
        )
        root.addView(selectedHeader)

        val hint = TextView(this).apply {
            text = getString(R.string.rec_picker_drag_hint)
            textSize = 12f
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val pad = (16 * density).toInt()
            setPadding(pad, 0, pad, (8 * density).toInt())
        }
        root.addView(hint)

        // RecyclerView selezionate
        val selectedRv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RecommendedPickerActivity)
            isNestedScrollingEnabled = false
        }
        selectedAdapter = SelectedAdapter()
        selectedRv.adapter = selectedAdapter

        val itemTouchHelper = ItemTouchHelper(SelectedDragCallback())
        itemTouchHelper.attachToRecyclerView(selectedRv)
        selectedAdapter.itemTouchHelper = itemTouchHelper

        root.addView(selectedRv)

        // Header sezione "Disponibili"
        availableHeader = sectionHeader(getString(R.string.rec_picker_available_header))
        availableHeader.setPadding(
            (16 * density).toInt(),
            (16 * density).toInt(),
            (16 * density).toInt(),
            (8 * density).toInt()
        )
        root.addView(availableHeader)

        // RecyclerView disponibili
        val availableRv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RecommendedPickerActivity)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            layoutParams = lp
        }
        availableAdapter = AvailableAdapter()
        availableRv.adapter = availableAdapter
        root.addView(availableRv)

        // Bottone Salva
        val saveBtn = MaterialButton(this).apply {
            text = getString(android.R.string.ok)
            cornerRadius = (24 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val mh = (16 * density).toInt()
            lp.setMargins(mh, (8 * density).toInt(), mh, (16 * density).toInt())
            layoutParams = lp
            setOnClickListener {
                settings.setRecommendedManualOrder(ordered.toList())
                finish()
            }
        }
        root.addView(saveBtn)

        setContentView(root)
        updateCounter()
    }

    private fun sectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveAttr(com.google.android.material.R.attr.colorPrimary))
            letterSpacing = 0.06f
            isAllCaps = true
        }
    }

    private fun updateCounter() {
        counterLabel.text = getString(R.string.rec_picker_counter, ordered.size, countNeeded)
        val color = if (ordered.size == countNeeded)
            resolveAttr(com.google.android.material.R.attr.colorPrimary)
        else
            resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        counterLabel.setTextColor(color)
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // ================== SELECTED ADAPTER (drag & drop) ==================
    inner class SelectedAdapter : RecyclerView.Adapter<SelectedVH>() {
        var itemTouchHelper: ItemTouchHelper? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedVH {
            val density = parent.context.resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (12 * density).toInt(), (8 * density).toInt(),
                    (12 * density).toInt(), (8 * density).toInt()
                )
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            // Drag handle
            val dragHandle = ImageView(parent.context).apply {
                setImageResource(R.drawable.ic_sort)
                setColorFilter(resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                val s = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (8 * density).toInt()
                }
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            }
            row.addView(dragHandle)
            // Numero
            val num = TextView(parent.context).apply {
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnPrimary))
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(resolveAttr(com.google.android.material.R.attr.colorPrimary))
                }
                val s = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (12 * density).toInt()
                }
            }
            row.addView(num)
            // Icon
            val icon = ImageView(parent.context).apply {
                val s = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (12 * density).toInt()
                }
            }
            row.addView(icon)
            // Label
            val label = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
                ellipsize = android.text.TextUtils.TruncateAt.END
                setSingleLine(true)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            row.addView(label)
            // Remove button
            val remove = ImageView(parent.context).apply {
                setImageResource(R.drawable.ic_delete_forever)
                setColorFilter(android.graphics.Color.parseColor("#E53935"))
                val s = (40 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
                isClickable = true; isFocusable = true
            }
            row.addView(remove)
            return SelectedVH(row, dragHandle, num, icon, label, remove)
        }

        override fun getItemCount() = ordered.size

        override fun onBindViewHolder(holder: SelectedVH, position: Int) {
            val key = ordered[position]
            val app = byKey[key]
            holder.num.text = (position + 1).toString()
            holder.icon.setImageDrawable(app?.icon)
            holder.label.text = app?.label ?: key
            holder.remove.setOnClickListener {
                val idx = holder.bindingAdapterPosition
                if (idx >= 0 && idx < ordered.size) {
                    ordered.removeAt(idx)
                    notifyItemRemoved(idx)
                    notifyItemRangeChanged(idx, ordered.size)
                    availableAdapter.refresh()
                    updateCounter()
                }
            }
            holder.dragHandle.setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }
        }

        fun moveItem(from: Int, to: Int) {
            if (from < 0 || to < 0 || from >= ordered.size || to >= ordered.size) return
            val item = ordered.removeAt(from)
            ordered.add(to, item)
            notifyItemMoved(from, to)
            // Aggiorno tutti i numeri
            notifyItemRangeChanged(0, ordered.size)
        }
    }

    class SelectedVH(
        v: View,
        val dragHandle: ImageView,
        val num: TextView,
        val icon: ImageView,
        val label: TextView,
        val remove: ImageView
    ) : RecyclerView.ViewHolder(v)

    inner class SelectedDragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            selectedAdapter.moveItem(from, to)
            return true
        }
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        override fun isLongPressDragEnabled() = false  // solo drag handle, no long press
    }

    // ================== AVAILABLE ADAPTER ==================
    inner class AvailableAdapter : RecyclerView.Adapter<AvailableVH>() {
        private var items: List<AppInfo> = computeAvailable()

        fun refresh() {
            items = computeAvailable()
            notifyDataSetChanged()
        }

        private fun computeAvailable(): List<AppInfo> {
            val ord = ordered.toSet()
            return available.filter { it.key !in ord }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvailableVH {
            val density = parent.context.resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setPadding(
                    (16 * density).toInt(), (12 * density).toInt(),
                    (16 * density).toInt(), (12 * density).toInt()
                )
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val icon = ImageView(parent.context).apply {
                val s = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (16 * density).toInt()
                }
            }
            row.addView(icon)
            val label = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSurface))
                ellipsize = android.text.TextUtils.TruncateAt.END
                setSingleLine(true)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            row.addView(label)
            val addBtn = ImageView(parent.context).apply {
                setImageResource(android.R.drawable.ic_input_add)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(resolveAttr(com.google.android.material.R.attr.colorPrimaryContainer))
                }
                setColorFilter(resolveAttr(com.google.android.material.R.attr.colorOnPrimaryContainer))
                val s = (36 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            }
            row.addView(addBtn)
            return AvailableVH(row, icon, label, addBtn)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: AvailableVH, position: Int) {
            val app = items[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            val canAdd = ordered.size < countNeeded
            holder.itemView.alpha = if (canAdd) 1f else 0.4f
            holder.itemView.isEnabled = canAdd
            holder.addBtn.alpha = if (canAdd) 1f else 0.4f
            val onAdd: (View) -> Unit = {
                if (ordered.size < countNeeded) {
                    ordered.add(app.key)
                    selectedAdapter.notifyItemInserted(ordered.size - 1)
                    selectedAdapter.notifyItemRangeChanged(0, ordered.size)
                    refresh()
                    updateCounter()
                }
            }
            holder.itemView.setOnClickListener(onAdd)
            holder.addBtn.setOnClickListener(onAdd)
        }
    }

    class AvailableVH(
        v: View,
        val icon: ImageView,
        val label: TextView,
        val addBtn: ImageView
    ) : RecyclerView.ViewHolder(v)
}
