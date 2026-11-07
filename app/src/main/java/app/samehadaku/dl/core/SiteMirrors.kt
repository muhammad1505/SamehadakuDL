package app.samehadaku.dl.core

import java.net.URI

/**
 * Known Samehadaku hosts. Every mirror serves the same paths, so a page can be moved
 * to another domain by swapping the host only. Dead upload links on the main domain
 * are often refreshed on a mirror, so the mirrors are the last resort before an
 * episode is reported as unavailable.
 */
object SiteMirrors {

    /** Path-compatible Samehadaku domains, most reliable first. */
    private val HOSTS = listOf(
        "samehadaku.how",
        "v2.samehadaku.how",
        "samehadaku.care",
        "samehadaku.li",
        "samehadaku.email",
        "samehadaku.vip",
        "samehadaku.mba",
    )

    /**
     * The same page on the other mirrors, in order. Returns an empty list for URLs that
     * are not Samehadaku pages or point at the site root, where swapping the host
     * tells nothing about the episode.
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
        return mirrorHosts(host).map { "https://$it$path${queryOf(uri)}" }
    }

    private fun mirrorHosts(host: String): List<String> =
        HOSTS.filterNot { it.equals(host, ignoreCase = true) }

    private fun queryOf(uri: URI): String = uri.rawQuery?.let { "?$it" }.orEmpty()
}
