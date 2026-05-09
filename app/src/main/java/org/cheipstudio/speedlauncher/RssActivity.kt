package org.cheipstudio.speedlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.w3c.dom.Element
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread

/**
 * v139: RSS reader minimalistico. Aggrega tutti i feed configurati in settings,
 * li parsa, mostra una lista di articoli ordinati per data. Tap → apri browser.
 */
class RssActivity : AppCompatActivity() {
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
    }


    data class Article(val title: String, val link: String, val source: String, val pubDateMs: Long)

    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private val items = mutableListOf<Article>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorSurface))
        }

        val toolbar = MaterialToolbar(this).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
            title = getString(R.string.rss_title)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(toolbar)

        progress = ProgressBar(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (24 * density).toInt()
            layoutParams = lp
        }
        root.addView(progress)

        emptyView = TextView(this).apply {
            text = getString(R.string.rss_empty)
            textSize = 15f
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (40 * density).toInt()
            layoutParams = lp
        }
        root.addView(emptyView)

        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RssActivity)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            layoutParams = lp
        }
        listView.adapter = RssAdapter(items) { article ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
            } catch (_: Throwable) {
                Toast.makeText(this, "Link non valido", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(listView)

        setContentView(root)
        loadFeeds()
        
        // v169: swipe destra→sinistra chiude (opposto del gesto di apertura)
        val gd = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: android.view.MotionEvent?, e2: android.view.MotionEvent,
                vx: Float, vy: Float
            ): Boolean {
                if (vx < -250f && kotlin.math.abs(vx) > kotlin.math.abs(vy) * 1.0f) {
                    finish()
                    return true
                }
                return false
            }
        })
        root.setOnTouchListener { _, ev -> 
            gd.onTouchEvent(ev)
            false  // non consumare, lascia che la list scorra normalmente
        }
    }

    private fun loadFeeds() {
        val feeds = SpeedApp.instance.settingsRepository.rssFeeds.value ?: emptyList<String>()
        if (feeds.isEmpty()) {
            progress.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        thread {
            val all = mutableListOf<Article>()
            for (feed in feeds) {
                try {
                    all.addAll(fetchFeed(feed))
                } catch (_: Throwable) { /* skip feed errore */ }
            }
            all.sortByDescending { it.pubDateMs }

            runOnUiThread {
                progress.visibility = View.GONE
                items.clear()
                items.addAll(all.take(50))
                listView.adapter?.notifyDataSetChanged()
                if (items.isEmpty()) emptyView.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchFeed(url: String): List<Article> {
        val out = mutableListOf<Article>()
        try {
            // v169: User-Agent realistico + redirect manuale http<->https (Java non li segue cross-protocol)
            var currentUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            var conn: java.net.HttpURLConnection? = null
            var redirects = 0
            while (redirects < 5) {
                val u = URL(currentUrl)
                conn = (u.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SpeedLauncher/1.0")
                    setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    currentUrl = if (loc.startsWith("http")) loc 
                                 else URL(URL(currentUrl), loc).toString()
                    conn.disconnect()
                    redirects++
                    continue
                }
                break
            }
            val finalConn = conn ?: return out
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = finalConn.inputStream.use { builder.parse(it) }
            doc.documentElement.normalize()

            // Source name (channel/title)
            val source = try {
                val ch = doc.getElementsByTagName("channel").item(0) as? Element
                (ch?.getElementsByTagName("title")?.item(0)?.textContent ?: url).take(40)
            } catch (_: Throwable) { url }

            // RSS 2.0: <item>; Atom: <entry>
            val items = doc.getElementsByTagName("item")
            val entries = doc.getElementsByTagName("entry")
            val nodes = if (items.length > 0) items else entries
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val title = el.getElementsByTagName("title").item(0)?.textContent?.trim() ?: continue
                val link = run {
                    // RSS: <link>http</link>; Atom: <link href="...">
                    val linkNode = el.getElementsByTagName("link").item(0) as? Element ?: return@run ""
                    linkNode.getAttribute("href").takeIf { it.isNotBlank() } ?: linkNode.textContent?.trim() ?: ""
                }
                if (link.isBlank()) continue
                val pubDate = el.getElementsByTagName("pubDate").item(0)?.textContent
                    ?: el.getElementsByTagName("published").item(0)?.textContent
                    ?: el.getElementsByTagName("updated").item(0)?.textContent ?: ""
                val ms = parsePubDate(pubDate)
                out.add(Article(title, link, source, ms))
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun parsePubDate(s: String): Long {
        if (s.isBlank()) return 0L
        val formats = listOf(
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (f in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(f, java.util.Locale.US)
                return sdf.parse(s)?.time ?: 0L
            } catch (_: Throwable) {}
        }
        return 0L
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private class RssAdapter(
        val items: List<Article>,
        val onClick: (Article) -> Unit
    ) : RecyclerView.Adapter<RssAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ll = v as LinearLayout
            val title: TextView = ll.getChildAt(0) as TextView
            val source: TextView = ll.getChildAt(1) as TextView
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true; isFocusable = true
                run {
                    val tvSel = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
                    setBackgroundResource(tvSel.resourceId)
                }
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val title = TextView(ctx).apply {
                textSize = 16f
                setTextColor(resolveAttrInt(ctx, com.google.android.material.R.attr.colorOnSurface))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            row.addView(title)
            val source = TextView(ctx).apply {
                textSize = 12f
                setTextColor(resolveAttrInt(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (4 * density).toInt()
                layoutParams = lp
            }
            row.addView(source)
            row.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return VH(row)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.source.text = item.source
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
        companion object {
            fun resolveAttrInt(ctx: android.content.Context, attr: Int): Int {
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(attr, tv, true)
                return tv.data
            }
        }
    }
}
