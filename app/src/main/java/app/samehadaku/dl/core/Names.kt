package app.samehadaku.dl.core

import java.util.Locale

object Names {

    /** Turns a slug such as `dragon-ball-super-beerus` into a readable title. */
    fun fromSlug(raw: String): String {
        val cleaned = raw
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .trim('/')
            .substringAfterLast('/')
        if (cleaned.isBlank()) return "Anime"
        return cleaned
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.length <= 3 && word.all { it.isDigit() }) {
                    word
                } else {
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) {
                            char.titlecase(Locale("id", "ID"))
                        } else {
                            char.toString()
                        }
                    }
                }
            }
    }

    /** `Kyouran Reijou: Nia Liston` -> `kyouran-reijou-nia-liston`. */
    fun toSlug(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unduhan" }
}
