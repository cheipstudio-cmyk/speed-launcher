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
import java.net.URL
import kotlin.concurrent.thread

/**
 * v139: RSS reader minimalistico. Aggrega tutti i feed configurati in settings,
 * li parsa, mostra una lista di articoli ordinati per data. Tap → apri browser.
 */
class RssActivity : AppCompatActivity() {
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.home_slide_in_back, R.anim.rss_slide_out_left_full)
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

        // v172: Outer container trasparente (mostra wallpaper sotto)
        val outer = android.widget.FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0)  // trasparente
        }
        
        // Card grande full-width con corner top arrotondati
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = (28 * density)
            cardElevation = (4 * density)
            setCardBackgroundColor(resolveAttr(com.google.android.material.R.attr.colorSurface))
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Margine top per status bar + padding laterale
            lp.topMargin = (32 * density).toInt()
            lp.leftMargin = (12 * density).toInt()
            lp.rightMargin = (12 * density).toInt()
            lp.bottomMargin = (12 * density).toInt()
            layoutParams = lp
        }
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.rss_title)
            setBackgroundColor(0)
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
            clipToPadding = false
            setPadding(0, (4 * density).toInt(), 0, (16 * density).toInt())
        }
        listView.adapter = RssAdapter(items) { article ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
            } catch (_: Throwable) {
                Toast.makeText(this, "Link non valido", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(listView)
        
        card.addView(root)
        outer.addView(card)
        setContentView(outer)
        loadFeeds()
        
        // v172: swipe destra→sinistra chiude — più tollerante
        val gd = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: android.view.MotionEvent?, e2: android.view.MotionEvent,
                vx: Float, vy: Float
            ): Boolean {
                if (vx < -180f && kotlin.math.abs(vx) > kotlin.math.abs(vy) * 0.8f) {
                    finish()
                    return true
                }
                return false
            }
        })
        outer.setOnTouchListener { _, ev -> 
            gd.onTouchEvent(ev)
            false
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
            android.util.Log.d("RssActivity", "Loading ${feeds.size} feeds: $feeds")
            for (feed in feeds) {
                try {
                    val result = fetchFeed(feed)
                    android.util.Log.d("RssActivity", "Feed $feed → ${result.size} articles")
                    all.addAll(result)
                } catch (t: Throwable) {
                    android.util.Log.e("RssActivity", "Feed $feed failed", t)
                }
            }
            all.sortByDescending { it.pubDateMs }
            android.util.Log.d("RssActivity", "Total articles: ${all.size}")

            runOnUiThread {
                progress.visibility = View.GONE
                items.clear()
                items.addAll(all.take(50))
                listView.adapter?.notifyDataSetChanged()
                if (items.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    Toast.makeText(this@RssActivity, "Nessun articolo. Controlla i log per dettagli.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchFeed(url: String): List<Article> {
        val out = mutableListOf<Article>()
        var currentUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        var conn: java.net.HttpURLConnection? = null
        try {
            // v171: redirect manuale + XmlPullParser (più tollerante di DocumentBuilder)
            var redirects = 0
            while (redirects < 5) {
                val u = URL(currentUrl)
                conn = (u.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                val code = conn.responseCode
                android.util.Log.d("RssActivity", "Fetch $currentUrl → $code")
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
            if (finalConn.responseCode !in 200..299) {
                android.util.Log.w("RssActivity", "HTTP error: ${finalConn.responseCode}")
                return out
            }
            
            // Parse con XmlPullParser nativo Android (più affidabile)
            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Usa charset del Content-Type quando disponibile, altrimenti UTF-8
            val charset = finalConn.contentType?.let { ct ->
                Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)
            } ?: "UTF-8"
            parser.setInput(finalConn.inputStream, charset)
            
            var sourceTitle = ""
            var inItem = false
            var inEntry = false
            var inChannel = false
            var depth = 0
            var currentTitle = ""
            var currentLink = ""
            var currentPubDate = ""
            var linkHref = ""  // per <link href="..."/> Atom
            var currentTag: String? = null
            
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        currentTag = tag
                        when (tag) {
                            "item" -> { inItem = true; currentTitle = ""; currentLink = ""; currentPubDate = ""; linkHref = "" }
                            "entry" -> { inEntry = true; currentTitle = ""; currentLink = ""; currentPubDate = ""; linkHref = "" }
                            "channel", "feed" -> inChannel = true
                            "link" -> {
                                // Atom: <link href="..." rel="alternate"/>
                                if (inItem || inEntry) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (rel == null || rel == "alternate") {
                                        val href = parser.getAttributeValue(null, "href")
                                        if (!href.isNullOrBlank()) linkHref = href
                                    }
                                }
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT, org.xmlpull.v1.XmlPullParser.CDSECT -> {
                        val text = parser.text ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> {
                                    if (inItem || inEntry) currentTitle += text
                                    else if (inChannel && sourceTitle.isEmpty()) sourceTitle += text
                                }
                                "link" -> {
                                    if ((inItem || inEntry)) currentLink += text
                                }
                                "pubDate", "published", "updated", "dc:date" -> {
                                    if (inItem || inEntry) currentPubDate += text
                                }
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        when (tag) {
                            "item", "entry" -> {
                                val titleTrim = currentTitle.trim()
                                val linkTrim = currentLink.trim().takeIf { it.isNotBlank() } ?: linkHref.trim()
                                if (titleTrim.isNotBlank() && linkTrim.isNotBlank()) {
                                    val ms = parsePubDate(currentPubDate.trim())
                                    val src = sourceTitle.trim().ifBlank { url }.take(40)
                                    out.add(Article(titleTrim, linkTrim, src, ms))
                                }
                                inItem = false; inEntry = false
                            }
                        }
                        currentTag = null
                    }
                }
                event = parser.next()
            }
            android.util.Log.d("RssActivity", "Parsed ${out.size} articles from $url")

        } catch (t: Throwable) {
            android.util.Log.e("RssActivity", "Fetch error for $currentUrl: ${t.message}", t)
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
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
