package app.samehadaku.dl.data.parser

import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Pure-Kotlin port of get_download_links() from the reference Python script.
 * The format is recovered from text nodes preceding the enclosing <ul>, exactly like
 * the BeautifulSoup previous_element walk in the original.
 */
object DownloadLinkParser {

    private val RESOLUTION_REGEX = Regex("(360|480|720|1080)P?")
    private val META_URL_REGEX = Regex("url=['\"]?([^'\">]+)", RegexOption.IGNORE_CASE)

    fun parse(html: String, pageUrl: String): DownloadLinks? = parse(Jsoup.parse(html, pageUrl))

    fun parse(document: Document): DownloadLinks? {
        val allNodes = ArrayList<Node>()
        collectNodes(document, allNodes)

        val linkData = linkedMapOf<VideoFormat, LinkedHashMap<String, LinkedHashMap<Host, String>>>()

        for (li in document.select("li")) {
            val anchors = li.select("a")
            if (anchors.isEmpty()) continue

            val valid = anchors.any { anchor ->
                val text = anchor.text().lowercase()
                val href = anchor.attr("href").lowercase()
                "pixeldrain" in text || "pixeldrain.com" in href ||
                    "mediafire" in text || "mediafire.com" in href
            }
            if (!valid) continue

            val liText = li.text().uppercase()
            val resolution = when {
                RESOLUTION_REGEX.find(liText) != null ->
                    RESOLUTION_REGEX.find(liText)!!.groupValues[1] + "p"
                "FULLHD" in liText -> "1080p"
                "HD" in liText -> "720p"
                else -> "HD"
            }

            val format = resolveFormat(li, allNodes)

            val hosts = linkData
                .getOrPut(format) { linkedMapOf() }
                .getOrPut(resolution) { linkedMapOf() }

            for (anchor in anchors) {
                val text = anchor.text().lowercase()
                val href = anchor.attr("href")
                if (href.isEmpty()) continue
                val lowerHref = href.lowercase()
                if ("pixeldrain" in text || "pixeldrain.com" in lowerHref) {
                    hosts[Host.PIXELDRAIN] = href
                } else if ("mediafire" in text || "mediafire.com" in lowerHref) {
                    hosts[Host.MEDIAFIRE] = href
                }
            }
        }

        val cleaned = linkData
            .mapValues { (_, resolutions) ->
                resolutions.filterValues { it.isNotEmpty() }
            }
            .filterValues { it.isNotEmpty() }

        return if (cleaned.isEmpty()) null else DownloadLinks(cleaned)
    }

    fun findMetaRefreshUrl(html: String, pageUrl: String): String? =
        findMetaRefreshUrl(Jsoup.parse(html, pageUrl), pageUrl)

    fun findMetaRefreshUrl(document: Document, pageUrl: String): String? {
        val meta = document.select("meta[http-equiv]").firstOrNull {
            it.attr("http-equiv").contains("refresh", ignoreCase = true)
        } ?: return null
        val match = META_URL_REGEX.find(meta.attr("content")) ?: return null
        val target = match.groupValues[1].trim()
        if (target.isEmpty()) return null
        return UrlUtils.resolve(UrlUtils.siteRoot(pageUrl), target)
    }

    private fun resolveFormat(li: Element, allNodes: List<Node>): VideoFormat {
        val ul = li.parents().firstOrNull { it.tagName().equals("ul", ignoreCase = true) }
            ?: return VideoFormat.MKV
        val ulIndex = allNodes.indexOfFirst { it === ul }
        if (ulIndex < 0) return VideoFormat.MKV

        var checked = 0
        var index = ulIndex - 1
        while (index >= 0 && checked < 30) {
            val node = allNodes[index]
            if (node is TextNode && !isInsideScriptOrStyle(node)) {
                checked++
                val text = node.text().uppercase()
                when {
                    "MP4" in text -> return VideoFormat.MP4
                    "X265" in text || "HEVC" in text -> return VideoFormat.X265
                    "MKV" in text -> return VideoFormat.MKV
                }
            }
            index--
        }
        return VideoFormat.MKV
    }

    private fun isInsideScriptOrStyle(node: Node): Boolean {
        var parent = node.parent()
        while (parent != null) {
            val tag = parent.nodeName().lowercase()
            if (tag == "script" || tag == "style") return true
            parent = parent.parent()
        }
        return false
    }

    private fun collectNodes(node: Node, output: MutableList<Node>) {
        output.add(node)
        for (child in node.childNodes()) collectNodes(child, output)
    }
}
