package org.cheipstudio.speedlauncher.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * v250: parser RSS riutilizzabile (estratto da RssActivity).
 * Estrae title, link, source, pubDate e imageUrl.
 */
object RssParser {

    data class Article(
        val title: String,
        val link: String,
        val source: String,
        val pubDateMs: Long,
        val imageUrl: String? = null
    )

    fun fetch(url: String): List<Article> {
        val out = mutableListOf<Article>()
        try {
            var conn: HttpURLConnection? = null
            var redirects = 0
            var currentUrl = url
            while (redirects < 5) {
                conn?.disconnect()
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 SpeedLauncher")
                    setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                    redirects++
                    continue
                }
                break
            }
            val finalConn = conn ?: return out
            if (finalConn.responseCode !in 200..299) return out
            val bodyBytes = finalConn.inputStream.use { it.readBytes() }
            if (bodyBytes.size < 50) return out

            val charset = finalConn.contentType?.let { ct ->
                Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)
            } ?: "UTF-8"

            var startOffset = 0
            if (bodyBytes.size >= 3 &&
                bodyBytes[0] == 0xEF.toByte() &&
                bodyBytes[1] == 0xBB.toByte() &&
                bodyBytes[2] == 0xBF.toByte()
            ) startOffset = 3
            while (startOffset < bodyBytes.size &&
                (bodyBytes[startOffset] == ' '.code.toByte() ||
                 bodyBytes[startOffset] == '\n'.code.toByte() ||
                 bodyBytes[startOffset] == '\r'.code.toByte() ||
                 bodyBytes[startOffset] == '\t'.code.toByte())
            ) startOffset++

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bodyBytes, startOffset, bodyBytes.size - startOffset), charset)

            fun normTag(name: String?): String {
                if (name == null) return ""
                val colon = name.indexOf(':')
                return (if (colon >= 0) name.substring(colon + 1) else name).lowercase()
            }

            var sourceTitle = ""
            var inItem = false
            var inEntry = false
            var inChannel = false
            var currentTitle = ""
            var currentLink = ""
            var currentPubDate = ""
            var currentDesc = ""
            var currentImage: String? = null
            var linkHref = ""
            var currentTag = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = normTag(parser.name)
                        currentTag = tag
                        when (tag) {
                            "item" -> { inItem = true; currentTitle = ""; currentLink = ""; currentPubDate = ""; currentDesc = ""; currentImage = null; linkHref = "" }
                            "entry" -> { inEntry = true; currentTitle = ""; currentLink = ""; currentPubDate = ""; currentDesc = ""; currentImage = null; linkHref = "" }
                            "channel", "feed" -> inChannel = true
                            "link" -> {
                                if (inItem || inEntry) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (rel == null || rel == "alternate") {
                                        val href = parser.getAttributeValue(null, "href")
                                        if (!href.isNullOrBlank()) linkHref = href
                                    }
                                }
                            }
                            "enclosure" -> {
                                if (inItem || inEntry) {
                                    val type = parser.getAttributeValue(null, "type")
                                    if (type?.startsWith("image") == true) {
                                        val u = parser.getAttributeValue(null, "url")
                                        if (!u.isNullOrBlank() && currentImage == null) currentImage = u
                                    }
                                }
                            }
                            "thumbnail", "media:thumbnail" -> {
                                if (inItem || inEntry) {
                                    val u = parser.getAttributeValue(null, "url")
                                    if (!u.isNullOrBlank() && currentImage == null) currentImage = u
                                }
                            }
                            "content", "media:content" -> {
                                if (inItem || inEntry) {
                                    val type = parser.getAttributeValue(null, "type")
                                    if (type?.startsWith("image") == true || type == null) {
                                        val u = parser.getAttributeValue(null, "url")
                                        if (!u.isNullOrBlank() && currentImage == null) currentImage = u
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        val text = parser.text ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> {
                                    if (inItem || inEntry) currentTitle += text
                                    else if (inChannel && sourceTitle.isEmpty()) sourceTitle += text
                                }
                                "link" -> if (inItem || inEntry) currentLink += text
                                "pubdate", "published", "updated", "date" -> if (inItem || inEntry) currentPubDate += text
                                "description", "summary", "content:encoded" -> if (inItem || inEntry) currentDesc += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = normTag(parser.name)
                        if (tag == "item" || tag == "entry") {
                            val titleTrim = currentTitle.trim()
                            val linkTrim = currentLink.trim().takeIf { it.isNotBlank() } ?: linkHref.trim()
                            if (titleTrim.isNotBlank() && linkTrim.isNotBlank()) {
                                val ms = parsePubDate(currentPubDate.trim())
                                val src = sourceTitle.trim().ifBlank { url }.take(40)
                                // se non ho immagine, prova ad estrarla dal description (img tag)
                                if (currentImage == null) currentImage = extractImgFromHtml(currentDesc)
                                out.add(Article(titleTrim, linkTrim, src, ms, currentImage))
                            }
                            inItem = false; inEntry = false
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun extractImgFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val m = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        return m?.groupValues?.get(1)
    }

    private fun parsePubDate(input: String): Long {
        if (input.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                return sdf.parse(input)?.time ?: continue
            } catch (_: Throwable) {}
        }
        return 0L
    }
}
