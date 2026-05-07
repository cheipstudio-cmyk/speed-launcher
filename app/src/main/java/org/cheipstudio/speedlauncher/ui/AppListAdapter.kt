package org.cheipstudio.speedlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.databinding.ItemAppBinding

class AppListAdapter(
    private val onClick: (AppInfo, View) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            binding.icon.setImageDrawable(app.icon)
            binding.label.text = app.label
            binding.root.setOnClickListener {
                Anim.pressFeedback(binding.icon)
                binding.icon.postDelayed({ onClick(app, binding.icon) }, 80)
            }
            binding.root.setOnLongClickListener {
                onLongClick(app, binding.icon)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) = oldItem.label == newItem.label
        }
    }
}
