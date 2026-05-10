package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.RssParser
import kotlin.concurrent.thread

/**
 * v250: pannello RSS feed inline come "leading page" della home.
 * Sostituisce RssActivity per integrazione fluida nello scroll pagine.
 */
class RssPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    companion object {
        private val bitmapCache = mutableMapOf<String, android.graphics.Bitmap>()
    }
    
    private val density = resources.displayMetrics.density
    private val items = mutableListOf<RssParser.Article>()
    private val filteredItems = mutableListOf<RssParser.Article>()
    private var sourceFilter: String? = null  // null = tutti
    private val sources = linkedSetOf<String>()

    private val titleView: TextView
    private val refreshBtn: MaterialButton
    private val filterScroll: HorizontalScrollView
    private val filterRow: LinearLayout
    private val recycler: RecyclerView
    private val emptyView: TextView
    private val progress: ProgressBar
    private val adapter = ArticleAdapter()

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, 0)

        // Header con titolo + refresh
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }
        titleView = TextView(context).apply {
            text = context.getString(R.string.rss_title)
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        header.addView(titleView)

        refreshBtn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.rss_refresh)
            isAllCaps = false
            cornerRadius = (24 * density).toInt()
            insetTop = 0; insetBottom = 0
            minimumHeight = (40 * density).toInt()
            setTextColor(Color.WHITE)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
            setOnClickListener { reload() }
        }
        header.addView(refreshBtn)
        addView(header)

        // Filtri sources (chip orizzontali scorribili)
        filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
            visibility = GONE
        }
        filterRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding((16 * density).toInt(), (4 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
        }
        filterScroll.addView(filterRow)
        addView(filterScroll)

        // Stato vuoto
        emptyView = TextView(context).apply {
            text = context.getString(R.string.rss_empty)
            textSize = 14f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (40 * density).toInt()
            layoutParams = lp
            visibility = GONE
        }
        addView(emptyView)

        // Progress
        progress = ProgressBar(context).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (24 * density).toInt()
            layoutParams = lp
            visibility = GONE
        }
        addView(progress)

        // Recycler articoli
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@RssPanelView.adapter
            clipToPadding = false
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), (24 * density).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            layoutParams = lp
        }
        addView(recycler)
    }

    /** Carica i feed da rete (chiamabile più volte per refresh) */
    fun reload() {
        progress.visibility = VISIBLE
        emptyView.visibility = GONE
        items.clear()
        sources.clear()
        adapter.notifyDataSetChanged()

        val urls = SpeedApp.instance.settingsRepository.rssFeeds.value ?: emptyList()
        if (urls.isEmpty()) {
            progress.visibility = GONE
            emptyView.text = context.getString(R.string.rss_no_feeds_configured)
            emptyView.visibility = VISIBLE
            return
        }

        thread {
            val all = mutableListOf<RssParser.Article>()
            for (url in urls) {
                try {
                    all.addAll(RssParser.fetch(url))
                } catch (_: Throwable) {}
            }
            // dedup by link, sort by date desc
            val unique = all.distinctBy { it.link }.sortedByDescending { it.pubDateMs }
            Handler(Looper.getMainLooper()).post {
                progress.visibility = GONE
                items.clear()
                items.addAll(unique)
                sources.clear()
                items.forEach { sources.add(it.source) }
                rebuildFilters()
                applyFilter()
            }
        }
    }

    private fun rebuildFilters() {
        filterRow.removeAllViews()
        if (sources.size < 2) {
            filterScroll.visibility = GONE
            return
        }
        filterScroll.visibility = VISIBLE
        // chip "Tutti"
        addFilterChip(context.getString(R.string.rss_filter_all), null)
        for (src in sources) {
            addFilterChip(src, src)
        }
    }

    private fun addFilterChip(label: String, value: String?) {
        val isSelected = sourceFilter == value
        val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            cornerRadius = (20 * density).toInt()
            insetTop = 0; insetBottom = 0
            minimumHeight = (36 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (36 * density).toInt()
            )
            lp.marginEnd = (8 * density).toInt()
            layoutParams = lp
            if (isSelected) {
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
                strokeColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
            } else {
                setBackgroundColor(0)
                setTextColor(Color.WHITE)
                strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
            }
            setOnClickListener {
                sourceFilter = value
                rebuildFilters()
                applyFilter()
            }
        }
        filterRow.addView(btn)
    }

    private fun applyFilter() {
        filteredItems.clear()
        if (sourceFilter == null) filteredItems.addAll(items)
        else filteredItems.addAll(items.filter { it.source == sourceFilter })
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (filteredItems.isEmpty()) VISIBLE else GONE
        if (filteredItems.isEmpty()) {
            emptyView.text = context.getString(R.string.rss_empty)
        }
    }

    // ============= Adapter =============

    inner class ArticleAdapter : RecyclerView.Adapter<ArticleVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleVH {
            return ArticleVH(buildItemView(parent.context))
        }
        override fun getItemCount(): Int = filteredItems.size
        override fun onBindViewHolder(holder: ArticleVH, position: Int) {
            holder.bind(filteredItems[position])
        }
    }

    inner class ArticleVH(val card: View) : RecyclerView.ViewHolder(card) {
        val image: ImageView = card.findViewById(R.id.rssArticleImage)
        val title: TextView = card.findViewById(R.id.rssArticleTitle)
        val source: TextView = card.findViewById(R.id.rssArticleSource)
        val date: TextView = card.findViewById(R.id.rssArticleDate)

        fun bind(article: RssParser.Article) {
            title.text = article.title
            source.text = article.source
            date.text = formatTime(article.pubDateMs)
            // immagine: glide se url valido, altrimenti gone
            if (!article.imageUrl.isNullOrBlank()) {
                image.visibility = VISIBLE
                image.setImageDrawable(null)
                image.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                loadBitmapAsync(article.imageUrl, image)
            } else {
                image.visibility = GONE
            }
            card.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    card.context.startActivity(intent)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun buildItemView(ctx: Context): View {
        val card = MaterialCardView(ctx).apply {
            cardElevation = 0f
            radius = (16 * density)
            useCompatPadding = false
            setCardBackgroundColor(Color.parseColor("#33000000"))
            strokeWidth = (1 * density).toInt()
            strokeColor = Color.parseColor("#22FFFFFF")
            val lp = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * density).toInt()
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }
        val container = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }
        val image = ImageView(ctx).apply {
            id = R.id.rssArticleImage
            scaleType = ImageView.ScaleType.CENTER_CROP
            val lp = LinearLayout.LayoutParams((80 * density).toInt(), (80 * density).toInt())
            lp.marginEnd = (12 * density).toInt()
            layoutParams = lp
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#33FFFFFF"))
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, 12 * density)
                }
            }
        }
        container.addView(image)
        val texts = LinearLayout(ctx).apply {
            orientation = VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val title = TextView(ctx).apply {
            id = R.id.rssArticleTitle
            textSize = 14f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }
        texts.addView(title)
        val meta = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * density).toInt()
            layoutParams = lp
        }
        val source = TextView(ctx).apply {
            id = R.id.rssArticleSource
            textSize = 11f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        meta.addView(source)
        val date = TextView(ctx).apply {
            id = R.id.rssArticleDate
            textSize = 11f
            setTextColor(Color.parseColor("#AAFFFFFF"))
        }
        meta.addView(date)
        texts.addView(meta)
        container.addView(texts)
        card.addView(container)
        return card
    }

    private fun loadBitmapAsync(url: String, target: ImageView) {
        // Check cache
        val cached = bitmapCache[url]
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        // Tag per evitare race condition (target riusato)
        target.tag = url
        thread(isDaemon = true) {
            try {
                val u = java.net.URL(url)
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.doInput = true
                val stream = conn.inputStream
                val bm = android.graphics.BitmapFactory.decodeStream(stream)
                stream.close()
                conn.disconnect()
                if (bm != null) {
                    synchronized(bitmapCache) {
                        if (bitmapCache.size > 60) bitmapCache.clear()
                        bitmapCache[url] = bm
                    }
                    Handler(Looper.getMainLooper()).post {
                        if (target.tag == url) {
                            target.setImageBitmap(bm)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - ms
        return when {
            diff < 60_000 -> "ora"
            diff < 3_600_000 -> "${diff / 60_000}m"
            diff < 86_400_000 -> "${diff / 3_600_000}h"
            else -> "${diff / 86_400_000}g"
        }
    }
}
