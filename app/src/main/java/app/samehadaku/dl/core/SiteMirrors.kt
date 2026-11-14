package app.samehadaku.dl.core

import java.net.URI

/**
 * Known Samehadaku hosts and how a page moves between them.
 *
 * There are two URL families:
 *  - [PRIMARY_HOSTS] use the canonical WordPress episode path (`/slug-episode-5/`).
 *  - [WORDPRESS_HOSTS] use the newer permalink (`/slug-episode-5-subtitle-indonesia/`)
 *    and occasionally a different anime slug (`ao-ashi-season-2` vs `ao-ashi-s2`).
 *
 * Only live hosts are listed. Retired domains such as `samehadaku.care` are still
 * recognised as Samehadaku URLs so old links can be rescued, but they are never
 * offered as a mirror themselves: they either park on a "moved" page or no longer
 * resolve. A slug that differs between families cannot be guessed by swapping the
 * host, so callers should use the search-based fallback in the repository.
 */
object SiteMirrors {

    /** Path-compatible Samehadaku domains, most reliable first. */
    private val PRIMARY_HOSTS = listOf(
        "v2.samehadaku.how",
        "samehadaku.how",
    )

    /** WordPress mirror with a different episode permalink, most reliable first. */
    private val WORDPRESS_HOSTS = listOf(
        "samehadaku.li",
    )

    /** Every live host, most reliable first; used for site-wide pages such as search. */
    fun knownHosts(): List<String> = PRIMARY_HOSTS + WORDPRESS_HOSTS

    /** Hosts that share the canonical episode path. */
    fun primaryHosts(): List<String> = PRIMARY_HOSTS

    /** Hosts that use the `-subtitle-indonesia` episode permalink. */
    fun wordpressHosts(): List<String> = WORDPRESS_HOSTS

    /** True when the host uses the canonical `/slug-episode-N/` path. */
    fun isPrimaryHost(host: String?): Boolean =
        host != null && PRIMARY_HOSTS.any { it.equals(host, ignoreCase = true) }

    /** True when the host uses the `-subtitle-indonesia` episode path. */
    fun isWordpressHost(host: String?): Boolean =
        host != null && WORDPRESS_HOSTS.any { it.equals(host, ignoreCase = true) }

    /**
     * The same page on the other mirrors, in order. Returns an empty list for URLs that
     * are not Samehadaku pages or point at the site root, where swapping the host
     * tells nothing about the episode.
     *
     * Episode URLs are also translated between URL families. For anime pages the
     * slug is left untouched; when the other family uses a different slug the
     * repository searches for the anime there instead.
     */
    fun candidates(url: String): List<String> {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return emptyList()
        }
        val host = uri.host ?: return emptyList()
        if (!UrlUtils.isSamehadakuHost(host)) return emptyList()
        val path = uri.path.orEmpty()
        if (path.isBlank() || path == "/") return emptyList()
        val query = queryOf(uri)
        val result = linkedSetOf<String>()

        if (isWordpressHost(host)) {
            val canonical = canonicalEpisodePath(path)
            for (primary in PRIMARY_HOSTS) {
                val targetPath = canonical ?: path
                result += "https://$primary$targetPath$query"
            }
        } else {
            for (primary in PRIMARY_HOSTS) {
                if (primary.equals(host, ignoreCase = true)) continue
                result += "https://$primary$path$query"
            }
            val episodePath = wordpressEpisodePath(path)
            if (episodePath != null) {
                for (wordpress in WORDPRESS_HOSTS) {
                    result += "https://$wordpress$episodePath$query"
                }
            }
        }
        return result.toList()
    }

    /** Anime slug and episode number recovered from a canonical or `.li` episode URL. */
    data class EpisodeRef(val animeSlug: String, val number: Int)

    fun episodeRef(url: String): EpisodeRef? {
        val path = try {
            URI(url).path.orEmpty()
        } catch (_: Exception) {
            return null
        }
        val segment = path.trim('/').substringAfterLast('/')
        if (segment.isBlank()) return null
        val match = EPISODE_SLUG.find(segment) ?: return null
        val slug = match.groupValues[1].trim('-')
        val number = match.groupValues[2].toIntOrNull() ?: return null
        if (slug.isBlank()) return null
        return EpisodeRef(slug, number)
    }

    /** Canonical `/slug-episode-N/` path for a primary host, or null. */
    fun canonicalEpisodePath(slug: String, number: Int): String =
        "/${slug.trim('/')}-episode-$number/"

    /** `.li` `/slug-episode-N-subtitle-indonesia/` path, or null when invalid. */
    fun wordpressEpisodePath(slug: String, number: Int): String =
        "/${slug.trim('/')}-episode-$number-subtitle-indonesia/"

    private fun canonicalEpisodePath(path: String): String? {
        val withoutSuffix = path.trimEnd('/').removeSuffix("-subtitle-indonesia")
        val segment = withoutSuffix.trimStart('/')
        return if (EPISODE_PATH.matches("$segment/")) "/$segment/" else null
    }

    private fun wordpressEpisodePath(path: String): String? {
        val normalized = path.trimEnd('/')
        val segment = normalized.trimStart('/')
        if (EPISODE_PATH.matches("$segment/")) {
            return "/$segment-subtitle-indonesia/"
        }
        return null
    }

    private fun queryOf(uri: URI): String = uri.rawQuery?.let { "?$it" }.orEmpty()

    private val EPISODE_SLUG = Regex(
        "^(.+?)-(?:episode|ep)-(\\d+)(?:-.*)?$",
        RegexOption.IGNORE_CASE,
    )

    private val EPISODE_PATH = Regex(
        "^[^/]+-(?:episode|ep)-\\d+(?:-[^/]+)*/$",
        RegexOption.IGNORE_CASE,
    )
}
