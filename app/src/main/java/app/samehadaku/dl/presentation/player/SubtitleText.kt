package app.samehadaku.dl.presentation.player

/**
 * Minimal text-subtitle parser used by the player's own subtitle layer.
 *
 * Media3 renders side-loaded subtitles directly, but it has no subtitle delay
 * control. Parsing SRT/WebVTT/SSA into plain cues lets the player draw the text
 * itself, so the "-0.1s / +0.1s" sync stepper is applied immediately without
 * reloading the file or rebuilding the media item.
 */
internal data class TextCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

internal fun parseSubtitleContent(fileName: String, content: String): List<TextCue> {
    if (content.isBlank()) return emptyList()
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "srt", "vtt", "webvtt" -> parseSrtOrVtt(content)
        "ssa", "ass" -> parseSsa(content)
        // Providers sometimes strip the extension from a picked document, so
        // sniff the content instead of refusing the file.
        else -> when {
            content.contains("-->") -> parseSrtOrVtt(content)
            content.lineSequence().any { it.startsWith("Dialogue:", ignoreCase = true) } -> parseSsa(content)
            else -> emptyList()
        }
    }
}

/** Returns the cue visible at [positionMs] once [offsetMs] is applied. */
internal fun activeTextCue(
    cues: List<TextCue>,
    positionMs: Long,
    offsetMs: Long,
): TextCue? {
    if (cues.isEmpty()) return null
    val target = positionMs + offsetMs
    // Overlapping cues happen in real files; the latest one that started wins.
    var match: TextCue? = null
    for (cue in cues) {
        if (cue.startMs > target) break
        if (target in cue.startMs..cue.endMs) match = cue
    }
    return match
}

private val SrtTime = Regex("""(\d{1,3}):(\d{2}):(\d{2})[.,](\d{1,3})""")
private val VttShortTime = Regex("""(\d{1,2}):(\d{2})[.,](\d{1,3})""")
private val HtmlTag = Regex("<[^>]*>")
// Android's regex engine (ICU) rejects an unescaped closing brace, unlike the
// desktop JVM, so both braces must be escaped or this file's initializer throws.
private val SsaTag = Regex("""\{[^}]*\}""")

private fun parseSrtOrVtt(content: String): List<TextCue> {
    val normalized = content
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    val blocks = normalized.split(Regex("""\n[ \t]*\n"""))
    val cues = mutableListOf<TextCue>()
    for (block in blocks) {
        val lines = block.lines()
        val timeIndex = lines.indexOfFirst { it.contains("-->") }
        if (timeIndex < 0) continue
        val times = lines[timeIndex].split("-->")
        if (times.size != 2) continue
        val start = parseTime(times[0].trim())
        // WebVTT may append cue settings after the end timestamp.
        val end = parseTime(times[1].trim().substringBefore(' '))
        if (start == null || end == null || end <= start) continue
        val text = lines.drop(timeIndex + 1)
            .joinToString("\n")
            .replace(HtmlTag, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
        if (text.isNotEmpty()) cues += TextCue(start, end, text)
    }
    return cues.sortedBy { it.startMs }
}

private fun parseTime(raw: String): Long? {
    SrtTime.find(raw)?.let { match ->
        val (hours, minutes, seconds, fraction) = match.destructured
        return toMs(hours, minutes, seconds, fraction)
    }
    VttShortTime.find(raw)?.let { match ->
        val (minutes, seconds, fraction) = match.destructured
        return toMs("0", minutes, seconds, fraction)
    }
    return null
}

private fun toMs(hours: String, minutes: String, seconds: String, fraction: String): Long {
    val millis = fraction.padEnd(3, '0').take(3).toLong()
    return hours.toLong() * 3_600_000 +
        minutes.toLong() * 60_000 +
        seconds.toLong() * 1_000 +
        millis
}

private fun parseSsa(content: String): List<TextCue> {
    val cues = mutableListOf<TextCue>()
    content.lineSequence()
        .filter { it.startsWith("Dialogue:", ignoreCase = true) }
        .forEach { line ->
            // Format: Dialogue: layer,start,end,style,name,marginL,marginR,marginV,effect,text
            val parts = line.substringAfter(':').split(',', limit = 10)
            if (parts.size < 10) return@forEach
            val start = parseSsaTime(parts[1].trim()) ?: return@forEach
            val end = parseSsaTime(parts[2].trim()) ?: return@forEach
            if (end <= start) return@forEach
            val text = parts[9]
                .replace(SsaTag, "")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .trim()
            if (text.isNotEmpty()) cues += TextCue(start, end, text)
        }
    return cues.sortedBy { it.startMs }
}

private fun parseSsaTime(raw: String): Long? {
    val match = Regex("""(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})""").find(raw) ?: return null
    val (hours, minutes, seconds, fraction) = match.destructured
    return toMs(hours, minutes, seconds, fraction)
}
