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

    /**
     * Canonical key for an anime slug or title. Mirrors use different spellings for
     * the same series (`ao-ashi-s2` vs `ao-ashi-season-2`), so season markers and
     * common page-title words are normalized before comparing them.
     */
    fun normalizedAnimeKey(raw: String): String {
        val cleaned = raw
            .lowercase(Locale.ROOT)
            .replace(Regex("\\bseason\\s*[-_.]?\\s*(\\d+)\\b"), " s$1 ")
            .replace(Regex("\\bs\\s*[-_.]?\\s*(\\d+)\\b"), " s$1 ")
            .replace(Regex("\\bsub(?:title)?\\s*[-_.]?\\s*(?:indo|indonesia)\\b"), " ")
            .replace(Regex("\\b(?:nonton|download|streaming)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        return cleaned
            .split(' ')
            .filter { token -> token.isNotBlank() && token !in IGNORED_TITLE_WORDS }
            .joinToString(" ")
    }

    /**
     * True only when both names describe the same series. Exact normalized equality
     * is intentional: accepting a close match once linked an episode of another anime.
     */
    fun sameAnime(left: String, right: String): Boolean {
        val a = normalizedAnimeKey(left)
        val b = normalizedAnimeKey(right)
        return a.isNotBlank() && a == b
    }

    /** Search cards often carry a display title while the URL only has the slug. */
    fun titleMatchesSlug(title: String, slug: String): Boolean = sameAnime(title, slug)

    private val IGNORED_TITLE_WORDS = setOf(
        "anime",
        "batch",
        "indo",
        "indonesia",
        "lengkap",
        "sub",
        "subtitle",
    )
}
