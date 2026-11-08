package app.samehadaku.dl.data.parser

import app.samehadaku.dl.core.Similarity
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.Episode
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Pure-Kotlin port of extract_episode_links() from the reference Python script.
 * No Android imports so it can be unit-tested on the JVM.
 */
object EpisodeListParser {

    private val EPISODE_REGEX = Regex("episode[-_/]?(\\d+)", RegexOption.IGNORE_CASE)
    private val EPISODE_TEXT_REGEX = Regex(
        "(?:episode|eps?)[\\s#:_-]*(\\d+)",
        RegexOption.IGNORE_CASE,
    )

    private val EPISODE_SELECTORS = listOf(
        ".lstepsiode a[href]",
        ".listeps a[href]",
        ".epsright a[href]",
        ".epsleft a[href]",
        "#episode a[href]",
        ".episodelist a[href]",
    )

    private data class Candidate(val url: String, val number: Int, val prefix: String)

    fun parse(html: String, pageUrl: String): List<Episode> {
        val document = Jsoup.parse(html, pageUrl)
        return parse(document, pageUrl)
    }

    fun parse(document: Document, pageUrl: String): List<Episode> {
        val pageUri = try {
            URI(pageUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        val pageHost = pageUri.host ?: return emptyList()
        val pageNorm = UrlUtils.normalizeUrl(pageUrl)
        val baseSlug = UrlUtils.lastPathSegment(pageUri.path.orEmpty())

        fun candidate(
            href: String?,
            label: String = "",
            fallbackNumber: Int? = null,
        ): Candidate? {
            if (href.isNullOrBlank()) return null
            val full = UrlUtils.resolve(pageUrl, href) ?: return null
            val uri = try {
                URI(full)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            if (!host.equals(pageHost, ignoreCase = true) && !UrlUtils.isSamehadakuHost(host)) {
                return null
            }
            val path = uri.path.orEmpty()
            if ("/anime/" in path || "/category/" in path || "/tag/" in path) return null

            val slug = UrlUtils.lastPathSegment(path)
            val match = EPISODE_REGEX.find(path)
            val textMatch = EPISODE_TEXT_REGEX.find(label)
            val number: Int
            val prefix: String
            if (match != null) {
                number = match.groupValues[1].toIntOrNull() ?: return null
                prefix = episodePrefix(slug)
            } else if (textMatch != null && baseSlug.isNotEmpty()) {
                // The redesigned site puts the episode number in the visible title
                // (for example, "Ao Ashi S2 Episode 2") while the href may be a
                // generic permalink. The List Episode block gives us enough context
                // to safely associate that link with the current anime.
                number = textMatch.groupValues[1].toIntOrNull() ?: return null
                prefix = baseSlug
            } else if (baseSlug.isNotEmpty() && slug == baseSlug) {
                number = 1
                prefix = baseSlug
            } else if (fallbackNumber != null && baseSlug.isNotEmpty()) {
                // Movie pages commonly use a clean permalink such as
                // /the-first-slam-dunk-movie/ and expose the episode number
                // only through the position in the List Episode block.
                number = fallbackNumber
                prefix = baseSlug
            } else {
                if (baseSlug.isEmpty()) return null
                val slugMatch = Regex(
                    Regex.escape(baseSlug) + "-(\\d+)",
                ).matchEntire(slug) ?: return null
                number = slugMatch.groupValues[1].toIntOrNull() ?: return null
                prefix = baseSlug
            }

            val normalized = UrlUtils.normalizeUrl(full)
            if (normalized == pageNorm) return null
            return Candidate(normalized, number, prefix)
        }

        val found = linkedMapOf<String, Int>()

        // Since the redesign, the episode list is a semantic block headed by
        // "List Episode" and its links are nested under h4 elements. Read this
        // block first so recommendation/sidebar links cannot become episodes.
        document.select("h3, h2").firstOrNull { heading ->
            heading.text().contains("List Episode", ignoreCase = true)
        }?.parent()?.select("h4 a[href]")?.forEachIndexed { index, anchor ->
            val item = candidate(
                href = anchor.attr("href"),
                label = anchor.text(),
                fallbackNumber = index + 1,
            ) ?: return@forEachIndexed
            found.putIfAbsent(item.url, item.number)
        }

        for (selector in EPISODE_SELECTORS) {
            if (found.isNotEmpty()) break
            val anchors = document.select(selector)
            for (anchor in anchors) {
                val item = candidate(anchor.attr("href"), anchor.text()) ?: continue
                found.putIfAbsent(item.url, item.number)
            }
            if (found.isNotEmpty()) break
        }

        if (found.isEmpty()) {
            val groups = linkedMapOf<String, LinkedHashMap<String, Int>>()
            for (anchor in document.select("a[href]")) {
                val item = candidate(anchor.attr("href"), anchor.text()) ?: continue
                groups.getOrPut(item.prefix) { linkedMapOf() }
                    .putIfAbsent(item.url, item.number)
            }

            var bestGroup: LinkedHashMap<String, Int>? = null
            var bestSimilarity = Double.NEGATIVE_INFINITY
            var bestSize = -1
            for ((prefix, group) in groups) {
                val similarity = Similarity.ratio(prefix, baseSlug)
                if (similarity > bestSimilarity ||
                    (similarity == bestSimilarity && group.size > bestSize)
                ) {
                    bestSimilarity = similarity
                    bestSize = group.size
                    bestGroup = group
                }
            }
            if (bestGroup != null) found.putAll(bestGroup)
        }

        return found.entries
            .sortedWith(compareBy({ it.value }, { it.key }))
            .map { Episode(number = it.value, url = it.key, slug = UrlUtils.lastPathSegment(it.key)) }
    }

    private fun episodePrefix(slug: String): String {
        val match = EPISODE_REGEX.find(slug) ?: return slug
        return slug.substring(0, match.range.first).trim('_', '-', ' ')
    }
}
