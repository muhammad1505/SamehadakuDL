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
        document.select("table tr, .schedule-item, .schedule .item, .jadwal .item, .listupd .bsx")
            .forEach { row ->
                val item = parseItem(row, pageUrl, null) ?: return@forEach
                found.putIfAbsent("${item.day}|${item.title}|${item.episode}", item)
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
                if (item != null) found.putIfAbsent("${item.day}|${item.title}|${item.episode}", item)
                sibling = current.nextElementSibling()
            }
        }
        return found.values.take(100)
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
        val titleElement = element.selectFirst("a[title], h2, h3, h4, .title, .tt, a[href]")
        val title = titleElement?.attr("title").orEmpty().ifBlank {
            titleElement?.text().orEmpty()
        }.clean()
        if (title.isBlank() || title.length < 2) return null
        val time = TIME_REGEX.find(text)?.value
        val episode = EPISODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { "Ep $it" }
        val status = when {
            text.contains("libur", ignoreCase = true) -> "Libur"
            text.contains("delay", ignoreCase = true) ||
                text.contains("tunda", ignoreCase = true) -> "Delay"
            else -> null
        }
        val href = titleElement?.let { it.attr("href").takeIf(String::isNotBlank) }
            ?.let { UrlUtils.resolve(pageUrl, it) }
        return ReleaseScheduleItem(day, title, time, episode, status, href)
    }

    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim()

    private val TIME_REGEX = Regex("\\b(?:[01]?\\d|2[0-3])[:.]\\d{2}\\b")
    private val EPISODE_REGEX = Regex("(?:ep(?:isode)?\\.?\\s*)(\\d+(?:\\.5)?)", RegexOption.IGNORE_CASE)
    private val ANNOUNCEMENT_REGEX = Regex("delay|libur|tunda|ditunda|jadwal anime", RegexOption.IGNORE_CASE)
}
