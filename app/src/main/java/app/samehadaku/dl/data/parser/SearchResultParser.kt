package app.samehadaku.dl.data.parser

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.AnimeSearchResult
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the anime cards on a Samehadaku search page (`/?s=<judul>`).
 *
 * The site has changed themes a few times, so the parser targets the current
 * `article.animpost` cards first and falls back to the older `bs`/`bsx` listings.
 * Any anchor that points at `/anime/<slug>/` is a valid last resort, which keeps
 * search working when only the card wrapper changes.
 */
object SearchResultParser {

    private val CARD_SELECTORS = listOf(
        "main.site-main.relat article",
        "main.relat article",
        ".anime-grid article",
        ".relat article",
        "main article.animpost",
        "article.animpost",
        ".schedule .animepost",
        ".result-schedule .animepost",
        ".listupd .animepost",
        ".animepost",
        ".listupd .bsx",
        ".listupd .bs",
        ".bsx",
        ".bs",
    )

    private val TITLE_SELECTORS = listOf(
        "img[title]",
        ".data .title h2",
        ".data h2",
        ".title h2",
        "h2",
        "h3",
        "h4",
        ".anime-title",
        ".ttls",
        ".tt",
        ".title",
        ".entry-title",
    )

    private val TYPE_SELECTORS = listOf(".type", ".typez", ".types")
    private val EPISODE_SELECTORS = listOf(".epx", ".ep", ".eps", ".episode")

    fun parse(html: String, pageUrl: String, query: String? = null): List<AnimeSearchResult> {
        val document = Jsoup.parse(html, pageUrl)
        return parse(document, pageUrl, query)
    }

    fun parse(
        document: Document,
        pageUrl: String,
        query: String? = null,
    ): List<AnimeSearchResult> {
        val pageHost = try {
            URI(pageUrl).host
        } catch (_: Exception) {
            null
        }

        val found = linkedMapOf<String, MergedResult>()

        for (selector in CARD_SELECTORS) {
            for (card in document.select(selector)) {
                val parsed = fromCard(card, pageUrl, pageHost) ?: continue
                val current = found[parsed.seriesSlug]
                found[parsed.seriesSlug] = current?.merge(parsed) ?: parsed
            }
            if (found.isNotEmpty()) break
        }

        if (found.isEmpty()) {
            for (anchor in document.select("a[href]")) {
                val parsed = fromAnchor(anchor, pageUrl, pageHost) ?: continue
                val current = found[parsed.seriesSlug]
                found[parsed.seriesSlug] = current?.merge(parsed) ?: parsed
            }
        }

        val results = found.values.map { merged ->
            AnimeSearchResult(
                title = merged.title,
                url = merged.detailUrl,
                slug = merged.seriesSlug,
                type = merged.type,
                episodeLabel = merged.episodeLabel,
            )
        }
        return if (query.isNullOrBlank()) {
            results.take(MAX_RESULTS)
        } else {
            // The site's search page mostly returns episode posts. Keeping only
            // related cards avoids showing random anime when the query has no
            // meaningful match (the old fallback returned every card).
            results
                .map { it to relevance(it.title, query) }
                .filter { (_, score) -> score >= 0 }
                .sortedWith(
                    compareByDescending<Pair<AnimeSearchResult, Int>> { it.second }
                        .thenBy { it.first.title.lowercase() },
                )
                .map { it.first }
                .take(MAX_RESULTS)
        }
    }

    private fun fromCard(
        card: Element,
        pageUrl: String,
        pageHost: String?,
    ): MergedResult? {
        var link: SeriesLink? = null
        var anchor: Element? = null
        for (candidate in card.select("a[href]")) {
            val href = UrlUtils.resolve(pageUrl, candidate.attr("href")) ?: continue
            val series = seriesLink(href, pageHost) ?: continue
            anchor = candidate
            link = series
            break
        }
        val selectedLink = link ?: return null
        val selectedAnchor = anchor ?: return null

        val rawTitle = TITLE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector) }
            .map { element -> element.attr("title").ifBlank { element.text() } }
            .firstOrNull { it.isNotBlank() }
            ?: selectedAnchor.attr("title").ifBlank { selectedAnchor.text() }
        val title = if (selectedLink.isListPage) {
            cleanTitle(rawTitle)
        } else {
            cleanSeriesTitle(rawTitle)
        }.ifBlank { Names.fromSlug(selectedLink.seriesSlug) }
        if (title.isBlank()) return null

        val type = TYPE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector)?.text() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val episodeText = EPISODE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector)?.text() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
        val episodeLabel = when {
            episodeText != null -> episodeText
            selectedLink.isBatch -> "Batch"
            !selectedLink.isListPage -> episodeLabelFromTitle(rawTitle)
            else -> null
        }

        return MergedResult(
            seriesSlug = selectedLink.seriesSlug,
            detailUrl = selectedLink.detailUrl,
            title = title,
            type = type,
            episodeLabel = episodeLabel,
            isListPage = selectedLink.isListPage,
            episodeNumber = episodeNumber(episodeLabel),
        )
    }

    private fun fromAnchor(
        anchor: Element,
        pageUrl: String,
        pageHost: String?,
    ): MergedResult? {
        val href = UrlUtils.resolve(pageUrl, anchor.attr("href")) ?: return null
        val series = seriesLink(href, pageHost) ?: return null
        val rawTitle = anchor.attr("title").ifBlank {
            anchor.selectFirst("h2, h3")?.text().orEmpty()
        }.ifBlank { anchor.text() }
        val title = if (series.isListPage) cleanTitle(rawTitle) else cleanSeriesTitle(rawTitle)
        return MergedResult(
            seriesSlug = series.seriesSlug,
            detailUrl = series.detailUrl,
            title = title.ifBlank { Names.fromSlug(series.seriesSlug) },
            type = null,
            episodeLabel = if (series.isBatch) "Batch" else episodeLabelFromTitle(rawTitle),
            isListPage = series.isListPage,
            episodeNumber = null,
        )
    }

    private data class MergedResult(
        val seriesSlug: String,
        val detailUrl: String,
        val title: String,
        val type: String?,
        val episodeLabel: String?,
        val isListPage: Boolean,
        val episodeNumber: Int?,
    ) {
        fun merge(other: MergedResult): MergedResult {
            val useOtherLink = other.isListPage && !isListPage
            val keepEpisode = when {
                episodeNumber == null -> other.episodeLabel
                other.episodeNumber == null -> episodeLabel
                other.episodeNumber > episodeNumber -> other.episodeLabel
                else -> episodeLabel
            }
            return copy(
                detailUrl = if (useOtherLink) other.detailUrl else detailUrl,
                title = if (useOtherLink) other.title else title,
                type = type ?: other.type,
                episodeLabel = keepEpisode,
                isListPage = isListPage || other.isListPage,
                episodeNumber = maxOf(episodeNumber ?: -1, other.episodeNumber ?: -1)
                    .takeIf { it >= 0 },
            )
        }
    }

    private data class SeriesLink(
        val seriesSlug: String,
        val detailUrl: String,
        val isListPage: Boolean,
        val isBatch: Boolean,
    )

    /**
     * Maps an article URL to the anime list page it belongs to. Search results
     * on Samehadaku are mostly `/slug-episode-12/` and `/batch/slug/` posts, not
     * `/anime/slug/` cards. Keeping only the latter made queries such as
     * "one piece" return a single unrelated title.
     */
    private fun seriesLink(url: String, pageHost: String?): SeriesLink? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host ?: return null
        val sameSite = UrlUtils.isSamehadakuHost(host) ||
            (pageHost != null && host.equals(pageHost, ignoreCase = true))
        if (!sameSite) return null

        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        if (segments.first().equals("anime", ignoreCase = true)) {
            val slug = segments.getOrNull(1).orEmpty()
            if (slug.isBlank()) return null
            return SeriesLink(
                seriesSlug = slug,
                detailUrl = listUrl(uri, slug),
                isListPage = true,
                isBatch = false,
            )
        }

        val raw = segments.last()
        val isBatch = segments.first().equals("batch", ignoreCase = true) ||
            BATCH_SLUG.containsMatchIn(raw)
        val seriesSlug = seriesSlugFromEpisode(raw)
            ?: if (isBatch) raw.replace(BATCH_SUFFIX, "") else null
        if (seriesSlug.isNullOrBlank() || seriesSlug.equals("anime", ignoreCase = true)) {
            return null
        }
        return SeriesLink(
            seriesSlug = seriesSlug,
            detailUrl = listUrl(uri, seriesSlug),
            isListPage = false,
            isBatch = isBatch,
        )
    }

    private fun listUrl(uri: URI, slug: String): String {
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return UrlUtils.normalizeUrl(uri.toString())
        return UrlUtils.normalizeUrl("$scheme://$host/anime/$slug/")
    }

    private fun seriesSlugFromEpisode(raw: String): String? {
        EPISODE_SLUG.find(raw)?.let { match ->
            return raw.substring(0, match.range.first)
        }
        EPISODE_SPECIAL_SLUG.find(raw)?.let { match ->
            return raw.substring(0, match.range.first)
        }
        return null
    }

    private fun episodeLabelFromTitle(raw: String): String? {
        EPISODE_NUMBER.find(raw)?.let { match ->
            return "Ep ${match.groupValues[1]}"
        }
        return if (EPISODE_SPECIAL.containsMatchIn(raw)) "Special" else null
    }

    private fun episodeNumber(label: String?): Int? {
        val raw = label ?: return null
        return EPISODE_NUMBER.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^(nonton|download|streaming)\\s+anime\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^anime\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+sub\\s*indo$", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun cleanSeriesTitle(raw: String): String = cleanTitle(raw)
        .replace(EPISODE_SUFFIX, "")
        .replace(EPISODE_SPECIAL, "")
        .replace(BATCH_MARKER, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun relevance(title: String, query: String): Int {
        val normalizedTitle = normalizeForSearch(title)
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedTitle == normalizedQuery) return 1000
        if (normalizedTitle.startsWith(normalizedQuery)) return 800
        val queryTokens = normalizedQuery.split(' ').filter { it.length > 1 }
        val matchingTokens = queryTokens.count { token -> normalizedTitle.contains(token) }
        return matchingTokens * 100 - normalizedTitle.length.coerceAtMost(99)
    }

    private fun normalizeForSearch(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private val EPISODE_NUMBER = Regex(
        "(?:episode|ep\\.?)\\s*(\\d+(?:[.,]\\d+)?)",
        RegexOption.IGNORE_CASE,
    )
    private val EPISODE_SPECIAL = Regex(
        "\\s*(?:[-–:]\\s*)?(?:episode|ep\\.?)\\s+(?:special|final)(?:\\s*\\([^)]*\\))?.*$",
        RegexOption.IGNORE_CASE,
    )
    private val EPISODE_SUFFIX = Regex(
        "\\s*(?:[-–:]\\s*)?(?:episode|ep\\.?)\\s*\\d+(?:[.,]\\d+)?(?:\\s*[-–]\\s*\\d+)?" +
            "(?:\\s*\\[[^\\]]*])?(?:\\s*\\([^)]*\\))?.*$",
        RegexOption.IGNORE_CASE,
    )
    private val BATCH_MARKER = Regex(
        "\\s*\\[(?:batch|end|hdts|bd|dvd|bluray)[^\\]]*].*$",
        RegexOption.IGNORE_CASE,
    )
    private val BATCH_SLUG = Regex("-batch(?:-|$)", RegexOption.IGNORE_CASE)
    private val BATCH_SUFFIX = Regex("-batch(?:-.*)?$", RegexOption.IGNORE_CASE)
    private val EPISODE_SLUG = Regex(
        "-(?:episode|ep)-\\d+(?:[.,]\\d+)?(?:-.*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val EPISODE_SPECIAL_SLUG = Regex(
        "-(?:episode|ep)-(?:special|final)(?:-.*)?$",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_RESULTS = 40
}
