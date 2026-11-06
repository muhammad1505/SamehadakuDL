package app.samehadaku.dl.data.parser

import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.TargetLink
import app.samehadaku.dl.domain.model.VideoFormat

/**
 * Pure-Kotlin port of find_target_links()/find_target_link().
 */
object HostSelector {

    fun findTargets(
        links: DownloadLinks,
        format: String,
        resolution: String,
        preferredHost: String,
    ): List<TargetLink> {
        val preferred = mutableListOf<TargetLink>()
        val others = mutableListOf<TargetLink>()
        val seen = linkedSetOf<String>()

        for ((formatKey, resolutions) in links.data) {
            if (!formatKey.label.contains(format, ignoreCase = true)) continue
            for ((resolutionKey, hosts) in resolutions) {
                if (!resolutionKey.contains(resolution, ignoreCase = true)) continue
                for ((host, url) in hosts) {
                    if (!seen.add(url)) continue
                    val target = TargetLink(host, url)
                    if (host.label.contains(preferredHost, ignoreCase = true)) {
                        preferred.add(target)
                    } else {
                        others.add(target)
                    }
                }
            }
        }
        return preferred + others
    }

    fun findFirst(
        links: DownloadLinks,
        format: String,
        resolution: String,
        preferredHost: String,
    ): String? = findTargets(links, format, resolution, preferredHost).firstOrNull()?.url

    /**
     * Nearest-quality fallback used only when the optional setting is enabled.
     * Prefers the chosen format, then other formats, and the closest resolution number.
     */
    fun findNearestTargets(
        links: DownloadLinks,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): NearestTargets? {
        val requested = resolution.filter(Char::isDigit).toIntOrNull() ?: 0
        val formatOrder = linkedSetOf<VideoFormat>().apply {
            add(format)
            addAll(links.formats().filter { it != format })
        }

        for (candidateFormat in formatOrder) {
            val resolutions = links.resolutions(candidateFormat)
            if (resolutions.isEmpty()) continue
            val chosen = resolutions.minWithOrNull(
                compareBy(
                    { kotlin.math.abs((it.filter(Char::isDigit).toIntOrNull() ?: 0) - requested) },
                    { it },
                ),
            ) ?: continue
            val targets = findTargets(
                links,
                candidateFormat.label,
                chosen,
                preferredHost.label,
            )
            if (targets.isNotEmpty()) {
                return NearestTargets(candidateFormat, chosen, targets)
            }
        }
        return null
    }

    data class NearestTargets(
        val format: VideoFormat,
        val resolution: String,
        val targets: List<TargetLink>,
    )
}
