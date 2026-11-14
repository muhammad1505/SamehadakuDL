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
     * Every quality candidate in the order the user expects:
     *  1. the configured format,
     *  2. MKV at the same resolution,
     *  3. MP4 at the same resolution,
     *  4. any remaining format at the same resolution.
     *
     * The resolution never changes here. A configured 480p download must not silently
     * become 720p just because another host offers it. Within each format the preferred
     * host is tried first, then the other hosts for that same quality.
     */
    fun findCandidates(
        links: DownloadLinks,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): List<Selection> {
        val order = linkedSetOf(format).apply {
            add(VideoFormat.MKV)
            add(VideoFormat.MP4)
            add(VideoFormat.X265)
            addAll(links.formats())
        }
        return order.mapNotNull { candidateFormat ->
            val entry = links.data[candidateFormat]
                ?.entries
                ?.firstOrNull { it.key.equals(resolution, ignoreCase = true) }
                ?: return@mapNotNull null
            if (entry.value.isEmpty()) return@mapNotNull null
            Selection(
                format = candidateFormat,
                resolution = entry.key,
                targets = orderedTargets(entry.value, preferredHost),
                adjusted = candidateFormat != format,
            )
        }
    }

    /** First candidate from [findCandidates], kept for callers that only need one. */
    fun findBestTargets(
        links: DownloadLinks,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): Selection? = findCandidates(links, format, resolution, preferredHost).firstOrNull()

    private fun orderedTargets(
        hosts: Map<Host, String>,
        preferredHost: Host,
    ): List<TargetLink> = hosts.entries
        .sortedBy { entry -> if (entry.key == preferredHost) 0 else 1 }
        .map { entry -> TargetLink(entry.key, entry.value) }

    data class Selection(
        val format: VideoFormat,
        val resolution: String,
        val targets: List<TargetLink>,
        /** True when the configured format was not available at this resolution. */
        val adjusted: Boolean,
    )

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
