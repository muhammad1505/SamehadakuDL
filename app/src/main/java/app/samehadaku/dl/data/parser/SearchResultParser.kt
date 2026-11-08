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
        ".listupd .bsx",
        ".listupd .bs",
        ".bsx",
        ".bs",
    )

    private val TITLE_SELECTORS = listOf(
        "img[title]",
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

    fun parse(html: String, pageUrl: String): List<AnimeSearchResult> {
        val document = Jsoup.parse(html, pageUrl)
        return parse(document, pageUrl)
    }

    fun parse(document: Document, pageUrl: String): List<AnimeSearchResult> {
        val pageHost = try {
            URI(pageUrl).host
        } catch (_: Exception) {
            null
        }

        val found = linkedMapOf<String, AnimeSearchResult>()

        for (selector in CARD_SELECTORS) {
            for (card in document.select(selector)) {
                val result = fromCard(card, pageUrl, pageHost) ?: continue
                found.putIfAbsent(result.url, result)
            }
            if (found.isNotEmpty()) break
        }

        if (found.isEmpty()) {
            for (anchor in document.select("a[href]")) {
                val result = fromAnchor(anchor, pageUrl, pageHost) ?: continue
                found.putIfAbsent(result.url, result)
            }
        }

        return found.values.take(MAX_RESULTS)
    }

    private fun fromCard(
        card: Element,
        pageUrl: String,
        pageHost: String?,
    ): AnimeSearchResult? {
        val anchor = card.select("a[href]").firstOrNull { link ->
            animeSlug(UrlUtils.resolve(pageUrl, link.attr("href")), pageUrl, pageHost) != null
        } ?: return null

        val href = UrlUtils.resolve(pageUrl, anchor.attr("href")) ?: return null
        val slug = animeSlug(href, pageUrl, pageHost) ?: return null

        val title = TITLE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector) }
            .map { element -> element.attr("title").ifBlank { element.text() } }
            .map(::cleanTitle)
            .firstOrNull { it.isNotBlank() }
            ?: cleanTitle(anchor.attr("title"))
        if (title.isBlank()) return null

        val type = TYPE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector)?.text() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        val episode = EPISODE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> card.selectFirst(selector)?.text() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }

        return AnimeSearchResult(
            title = title,
            url = UrlUtils.normalizeUrl(href),
            slug = slug,
            type = type,
            episodeLabel = episode,
        )
    }

    private fun fromAnchor(
        anchor: Element,
        pageUrl: String,
        pageHost: String?,
    ): AnimeSearchResult? {
        val href = UrlUtils.resolve(pageUrl, anchor.attr("href")) ?: return null
        val slug = animeSlug(href, pageUrl, pageHost) ?: return null
        val title = cleanTitle(
            anchor.attr("title").ifBlank {
                anchor.selectFirst("h2, h3")?.text().orEmpty()
            }.ifBlank { anchor.text() },
        ).ifBlank { Names.fromSlug(slug) }
        return AnimeSearchResult(
            title = title,
            url = UrlUtils.normalizeUrl(href),
            slug = slug,
        )
    }

    /** Returns the anime slug for a same-site `/anime/<slug>/` URL, else null. */
    private fun animeSlug(url: String?, pageUrl: String, pageHost: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host ?: return null
        val sameSite = UrlUtils.isSamehadakuHost(host) ||
            (pageHost != null && host.equals(pageHost, ignoreCase = true))
        if (!sameSite) return null
        val path = uri.path.orEmpty()
        if (!path.contains("/anime/")) return null
        val slug = UrlUtils.lastPathSegment(path)
        if (slug.isBlank() || slug.equals("anime", ignoreCase = true)) return null
        if (path.removePrefix("/anime/").trim('/').isBlank()) return null
        return slug
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^(nonton|download|streaming)\\s+anime\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^anime\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+sub\\s*indo$", RegexOption.IGNORE_CASE), "")
        .trim()

    private const val MAX_RESULTS = 40
}
