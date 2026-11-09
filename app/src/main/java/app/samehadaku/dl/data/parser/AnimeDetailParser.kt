package app.samehadaku.dl.data.parser

import app.samehadaku.dl.domain.model.AnimeDetail
import org.jsoup.nodes.Document

object AnimeDetailParser {

    fun parse(document: Document): AnimeDetail? {
        // The meta description on the current site is only a short SEO sentence.
        // Prefer the visible `.desc` block so the detail page receives the full
        // synopsis (for example the complete Gol D. Roger / Luffy description).
        val visibleDescriptions = document.select(
            ".infox .desc, .desc, .description, .synp, [itemprop=description], " +
                ".entry-content > p, .entry-content .description",
        ).map { it.text().clean() }
            .filter { it.length >= 18 }
            .filterNot { it.contains("list episode", ignoreCase = true) }
        val description = (visibleDescriptions.maxByOrNull(String::length)
            ?: sequenceOf(
                document.selectFirst("meta[property=og:description]")?.attr("content"),
                document.selectFirst("meta[name=description]")?.attr("content"),
            ).mapNotNull { it?.clean() }.firstOrNull { it.length >= 18 })
            ?.limitDescription()

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

        val details = document.select(
            ".spe span, .info-content .spe span, .anime-info span, .animinfo span",
        ).mapNotNull { element ->
            val label = element.selectFirst("b, strong, .label")?.text()?.clean()
                ?.trimEnd(':') ?: return@mapNotNull null
            val value = element.text().clean().removePrefix(label).trim().trimStart(':').trim()
            label.lowercase() to value.takeIf { it.isNotBlank() }
        }.toMap()

        if (description == null && rating == null && genres.isEmpty() && details.isEmpty()) return null
        return AnimeDetail(
            description = description,
            rating = rating,
            genres = genres,
            status = details.valueFor("status"),
            type = details.valueFor("type"),
            source = details.valueFor("source"),
            duration = details.valueFor("duration"),
            totalEpisodes = details.valueFor("total episode", "total episodes"),
            season = details.valueFor("season"),
            studio = details.valueFor("studio"),
            producers = details.valueFor("producers", "producer"),
            released = details.valueFor("released"),
        )
    }

    private fun Map<String, String?>.valueFor(vararg labels: String): String? =
        labels.asSequence().mapNotNull { this[it] }.firstOrNull()

    private fun String.limitDescription(): String =
        if (length <= MAX_DESCRIPTION_LENGTH) this else take(MAX_DESCRIPTION_LENGTH)
            .substringBeforeLast(' ').trimEnd() + "…"

    private fun ratingFromText(text: String): String? {
        val match = RATING_REGEX.find(text) ?: return null
        return match.groupValues.getOrNull(1)
    }

    private fun ratingValue(raw: String): String? {
        val match = RATING_REGEX.find(raw) ?: NUMBER_REGEX.find(raw) ?: return null
        return match.groupValues.lastOrNull()?.replace(',', '.')
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private const val MAX_DESCRIPTION_LENGTH = 1800

    private val RATING_REGEX = Regex(
        "(?:rating|score|skor|nilai)[^0-9]{0,20}([0-9]{1,2}(?:[.,][0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER_REGEX = Regex("\\b([0-9](?:[.,][0-9]{1,2})?)\\s*(?:/\\s*10)?\\b")
}
