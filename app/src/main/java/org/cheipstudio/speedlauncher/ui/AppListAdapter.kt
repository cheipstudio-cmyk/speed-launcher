package org.cheipstudio.speedlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.data.SettingsRepository
import org.cheipstudio.speedlauncher.databinding.ItemAppBinding

/**
 * v20: applica IconShaper come la home + supporta layout LIST.
 */
class AppListAdapter(
    private val onClick: (AppInfo, View) -> Unit,
    private val onLongPress: ((AppInfo) -> Unit)? = null
) : ListAdapter<AppInfo, RecyclerView.ViewHolder>(DIFF) {

    var listMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int = if (listMode) VT_LIST else VT_GRID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VT_LIST) {
            ListVH(inflater.inflate(R.layout.item_app_list, parent, false))
        } else {
            GridVH(ItemAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val app = getItem(position)
        when (holder) {
            is GridVH -> holder.bind(app)
            is ListVH -> holder.bind(app)
        }
    }

    inner class GridVH(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            val shape = SpeedApp.instance.settingsRepository.iconShape.value
                ?: SettingsRepository.SHAPE_ORIGINAL
            binding.icon.setImageDrawable(IconShaper.shape(app.icon, shape))
            binding.label.text = app.label
            binding.root.setOnClickListener {
                Anim.pressFeedback(binding.icon)
                binding.icon.postDelayed({ onClick(app, binding.icon) }, 60)
            }
            binding.root.setOnLongClickListener {
                onLongPress?.invoke(app)
                true
            }
        }
    }

    inner class ListVH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.iconList)
        private val label = view.findViewById<TextView>(R.id.labelList)
        fun bind(app: AppInfo) {
            val shape = SpeedApp.instance.settingsRepository.iconShape.value
                ?: SettingsRepository.SHAPE_ORIGINAL
            icon.setImageDrawable(IconShaper.shape(app.icon, shape))
            label.text = app.label
            itemView.setOnClickListener {
                Anim.pressFeedback(icon)
                icon.postDelayed({ onClick(app, icon) }, 60)
            }
            itemView.setOnLongClickListener {
                onLongPress?.invoke(app)
                true
            }
        }
    }

    companion object {
        private const val VT_GRID = 0
        private const val VT_LIST = 1
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.label == newItem.label
        }
    }
}
