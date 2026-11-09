package app.samehadaku.dl.data.parser

import app.samehadaku.dl.domain.model.AnimeDetail
import org.jsoup.nodes.Document

object AnimeDetailParser {

    fun parse(document: Document): AnimeDetail? {
        val description = sequenceOf(
            document.selectFirst("meta[property=og:description]")?.attr("content"),
            document.selectFirst("meta[name=description]")?.attr("content"),
            document.selectFirst(".infox .desc, .desc, .synp, .entry-content")?.text(),
        ).mapNotNull { it?.clean() }.firstOrNull { it.length >= 18 }

        val rating = sequenceOf(
            document.selectFirst(".rating, .score, .rating strong, .infox .rating")?.text(),
            document.body()?.text()?.let(::ratingFromText),
        ).mapNotNull { it?.let(::ratingValue) }.firstOrNull()

        val genres = document.select(
            ".genres a, .genxed a, .infox .genre a, a[href*='/genre/']",
        ).map { it.text().clean() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        if (description == null && rating == null && genres.isEmpty()) return null
        return AnimeDetail(description = description, rating = rating, genres = genres)
    }

    private fun ratingFromText(text: String): String? {
        val match = RATING_REGEX.find(text) ?: return null
        return match.groupValues.getOrNull(1)
    }

    private fun ratingValue(raw: String): String? {
        val match = RATING_REGEX.find(raw) ?: NUMBER_REGEX.find(raw) ?: return null
        return match.groupValues.lastOrNull()?.replace(',', '.')
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private val RATING_REGEX = Regex(
        "(?:rating|score|skor|nilai)[^0-9]{0,20}([0-9]{1,2}(?:[.,][0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER_REGEX = Regex("\\b([0-9](?:[.,][0-9]{1,2})?)\\s*(?:/\\s*10)?\\b")
}
