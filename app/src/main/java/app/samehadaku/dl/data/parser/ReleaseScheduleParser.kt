package app.samehadaku.dl.data.parser

import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ReleaseScheduleParser {

    private val DAY_NAMES = listOf(
        "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu",
    )

    fun parseSchedule(document: Document, pageUrl: String): List<ReleaseScheduleItem> {
        val found = linkedMapOf<String, ReleaseScheduleItem>()

        // Current Samehadaku renders one `.result-schedule` block per weekday.
        // The day is stored in Alpine's x-show expression, while each anime card
        // contains its poster, score, title and release time.
        document.select(".schedule .result-schedule .animepost, .result-schedule .animepost")
            .forEach { card ->
                val day = dayFromScheduleBlock(card)
                val item = parseItem(card, pageUrl, day) ?: return@forEach
                found.putIfAbsent(keyOf(item), item)
            }

        document.select("table tr, .schedule-item, .schedule .item, .jadwal .item, .listupd .bsx")
            .forEach { row ->
                val item = parseItem(row, pageUrl, null) ?: return@forEach
                found.putIfAbsent(keyOf(item), item)
            }

        // Some mirrors render each weekday as a heading followed by a plain list.
        DAY_NAMES.forEach { day ->
            val heading = document.select("h2, h3, h4, .day, .schedule-title")
                .firstOrNull { it.text().contains(day, ignoreCase = true) }
                ?: return@forEach
            var sibling = heading.nextElementSibling()
            repeat(12) {
                val current = sibling ?: return@repeat
                val item = parseItem(current, pageUrl, day)
                if (item != null) found.putIfAbsent(keyOf(item), item)
                sibling = current.nextElementSibling()
            }
        }
        return found.values.take(100)
    }

    private fun dayFromScheduleBlock(element: Element): String? {
        val block = element.parents().firstOrNull { parent ->
            parent.hasAttr("x-show") && parent.hasClass("result-schedule")
        } ?: element.parents().firstOrNull { it.hasClass("result-schedule") }
        val expression = block?.attr("x-show").orEmpty().lowercase()
        val token = Regex("activeday\\s*===\\s*['\"]([^'\"]+)").find(expression)
            ?.groupValues?.getOrNull(1)
        return dayFromToken(token)
    }

    fun parseAnnouncements(document: Document): List<String> {
        val candidates = document.select(
            "title, h1, h2, h3, .alert, .notice, .announcement, .schedule-status, " +
                ".widget-title, .infobox",
        ).map { it.text().clean() }
        return candidates.filter { text ->
            text.length in 12..180 && ANNOUNCEMENT_REGEX.containsMatchIn(text)
        }.distinct().take(3)
    }

    private fun parseItem(
        element: Element,
        pageUrl: String,
        fallbackDay: String?,
    ): ReleaseScheduleItem? {
        val text = element.text().clean()
        if (text.isBlank()) return null
        val day = fallbackDay ?: DAY_NAMES.firstOrNull { text.contains(it, ignoreCase = true) }
            ?: element.parents().firstNotNullOfOrNull { parent ->
                DAY_NAMES.firstOrNull { parent.text().contains(it, ignoreCase = true) }
            }
            ?: "Minggu ini"
        val titleElement = element.selectFirst(
            ".data .title h2, .title h2, h2, a[title], h3, h4, .title, .tt, a[href]",
        )
        val title = titleElement?.attr("title").orEmpty().ifBlank {
            titleElement?.text().orEmpty()
        }.clean()
        if (title.isBlank() || title.length < 2) return null
        val time = TIME_REGEX.find(text)?.value
        val episode = EPISODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { "Ep $it" }
        val cover = element.selectFirst("img.anmsa, img[itemprop=image], img[src], img[data-src]")
            ?.let { image ->
                sequenceOf(
                    image.absUrl("src"),
                    image.absUrl("data-src"),
                    image.attr("src"),
                    image.attr("data-src"),
                ).firstOrNull(String::isNotBlank)
            }
            ?.let { UrlUtils.resolve(pageUrl, it) }
        val rating = element.selectFirst(".score, .rating")?.text()?.let { raw ->
            RATING_REGEX.find(raw)?.groupValues?.getOrNull(1)
                ?: SCORE_REGEX.find(raw)?.groupValues?.getOrNull(1)
        }
        val status = when {
            text.contains("libur", ignoreCase = true) -> "Libur"
            text.contains("delay", ignoreCase = true) ||
                text.contains("tunda", ignoreCase = true) -> "Delay"
            else -> null
        }
        val href = titleElement?.let { it.attr("href").takeIf(String::isNotBlank) }
            ?.let { UrlUtils.resolve(pageUrl, it) }
        return ReleaseScheduleItem(day, title, time, episode, status, href, cover, rating)
    }

    private fun keyOf(item: ReleaseScheduleItem): String =
        "${item.day}|${item.title.lowercase()}|${item.time}|${item.url}"

    private fun dayFromToken(token: String?): String? = when (token?.lowercase()) {
        "monday" -> "Senin"
        "tuesday" -> "Selasa"
        "wednesday" -> "Rabu"
        "thursday" -> "Kamis"
        "friday" -> "Jumat"
        "saturday" -> "Sabtu"
        "sunday" -> "Minggu"
        else -> token?.let { DAY_NAMES.firstOrNull { day -> it.contains(day, true) } }
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private val TIME_REGEX = Regex("\\b(?:[01]?\\d|2[0-3])[:.]\\d{2}\\b")
    private val EPISODE_REGEX = Regex("(?:ep(?:isode)?\\.?\\s*)(\\d+(?:\\.5)?)", RegexOption.IGNORE_CASE)
    private val RATING_REGEX = Regex("(?:score|rating|skor)?[^0-9]{0,4}([0-9]{1,2}[.,][0-9]{1,2})", RegexOption.IGNORE_CASE)
    private val SCORE_REGEX = Regex("\\b([0-9]{1,2}(?:[.,][0-9]{1,2})?)\\b")
    private val ANNOUNCEMENT_REGEX = Regex("delay|libur|tunda|ditunda|jadwal anime", RegexOption.IGNORE_CASE)
}
