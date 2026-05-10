package org.cheipstudio.speedlauncher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.ui.IconShaper

/**
 * v27: schermata per mostrare/ripristinare le app nascoste dal drawer.
 */
class HiddenAppsActivity : AppCompatActivity() {

    private lateinit var adapter: HiddenAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        val tv = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)
        window.statusBarColor = tv.data
        window.navigationBarColor = tv.data
        setContentView(R.layout.activity_hidden_apps)
        // v203: fade-in immediato decorView per apertura percepita più veloce
        try {
            val dv = window.decorView
            dv.alpha = 0f
            dv.animate().alpha(1f).setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } catch (_: Throwable) {}
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        emptyView = findViewById(R.id.emptyText)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = HiddenAdapter()
        recycler.adapter = adapter

        refreshList()
    }

    private fun refreshList() {
        val settings = SpeedApp.instance.settingsRepository
        val all = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val hidden = settings.hiddenApps.value ?: mutableSetOf()
        val list = all.filter { hidden.contains(it.key) }.sortedBy { it.label.lowercase() }
        adapter.submit(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class HiddenAdapter : RecyclerView.Adapter<HiddenAdapter.VH>() {
        private val items = mutableListOf<AppInfo>()
        fun submit(list: List<AppInfo>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_hidden_app, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val icon = v.findViewById<ImageView>(R.id.icon)
            private val label = v.findViewById<TextView>(R.id.label)
            private val unhideBtn = v.findViewById<TextView>(R.id.unhideBtn)
            fun bind(app: AppInfo) {
                val shape = SpeedApp.instance.settingsRepository.iconShape.value
                    ?: org.cheipstudio.speedlauncher.data.SettingsRepository.SHAPE_ORIGINAL
                icon.setImageDrawable(IconShaper.shape(app.icon, shape))
                label.text = app.label
                unhideBtn.setOnClickListener {
                    SpeedApp.instance.settingsRepository.unhideApp(app.key)
                    refreshList()
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            overridePendingTransition(R.anim.slide_in_left_back, R.anim.slide_out_right)
        }
    }

}
