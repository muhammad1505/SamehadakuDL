package app.samehadaku.dl.data.parser

import app.samehadaku.dl.core.UrlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Extracts the anime poster URL from a detail page without relying on one theme. */
object AnimeCoverParser {

    private val IMAGE_SELECTORS = listOf(
        ".thumb img[src], .thumb img[data-src]",
        ".bigcover img[src], .bigcover img[data-src]",
        ".poster img[src], .poster img[data-src]",
        ".anime-card img[src], .anime-card img[data-src]",
        ".infox img[src], .infox img[data-src]",
    )

    fun parse(html: String, pageUrl: String): String? =
        parse(Jsoup.parse(html, pageUrl), pageUrl)

    fun parse(document: Document, pageUrl: String): String? {
        val social = document.select("meta[property=og:image], meta[name=twitter:image]")
            .mapNotNull { it.attr("content").takeIf(String::isNotBlank) }
            .firstOrNull()
        val source = social ?: IMAGE_SELECTORS
            .asSequence()
            .flatMap { selector -> document.select(selector).asSequence() }
            .mapNotNull { image ->
                sequenceOf(
                    image.absUrl("src"),
                    image.absUrl("data-src"),
                    image.attr("src"),
                    image.attr("data-src"),
                ).firstOrNull(String::isNotBlank)
            }
            .firstOrNull()
        return source?.let { UrlUtils.resolve(pageUrl, it) }
            ?.takeIf { UrlUtils.isHttpUrl(it) }
    }
}
