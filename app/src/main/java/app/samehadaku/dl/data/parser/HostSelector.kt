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
     * Best links for a requested quality. An exact format + resolution always wins.
     * When that is not uploaded for an episode, the closest available quality is used
     * instead: same resolution on another format first (preferred host first), then the
     * nearest resolution. This is what lets a saved X265 480p Pixeldrain preference
     * download the MKV 480p Pixeldrain upload of a new episode without user input.
     */
    fun findBestTargets(
        links: DownloadLinks,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): Selection? {
        exactTargets(links, format, resolution, preferredHost)?.let { exact ->
            if (exact.isNotEmpty()) {
                return Selection(
                    format = format,
                    resolution = resolution,
                    targets = exact,
                    adjusted = false,
                )
            }
        }

        val requested = resolution.filter(Char::isDigit).toIntOrNull() ?: 0
        val candidates = buildList {
            for (candidateFormat in links.formats()) {
                for ((candidateResolution, hosts) in links.data[candidateFormat].orEmpty()) {
                    if (hosts.isEmpty()) continue
                    if (candidateFormat == format &&
                        candidateResolution.equals(resolution, ignoreCase = true)
                    ) {
                        continue
                    }
                    val number = candidateResolution.filter(Char::isDigit).toIntOrNull() ?: 0
                    add(
                        Candidate(
                            format = candidateFormat,
                            resolution = candidateResolution,
                            number = number,
                            distance = kotlin.math.abs(number - requested),
                            hasPreferredHost = preferredHost in hosts,
                            targets = orderedTargets(hosts, preferredHost),
                        ),
                    )
                }
            }
        }

        val best = candidates.minWithOrNull(
            compareBy<Candidate>(
                { if (it.hasPreferredHost) 0 else 1 },
                { it.distance },
                { if (it.format == format) 0 else 1 },
                { -it.number },
            ),
        ) ?: return null

        return Selection(
            format = best.format,
            resolution = best.resolution,
            targets = best.targets,
            adjusted = true,
        )
    }

    private fun exactTargets(
        links: DownloadLinks,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): List<TargetLink>? {
        val hosts = links.data[format]
            ?.entries
            ?.firstOrNull { it.key.equals(resolution, ignoreCase = true) }
            ?.value
            ?: return null
        if (hosts.isEmpty()) return null
        return orderedTargets(hosts, preferredHost)
    }

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
        /** True when the exact requested format/resolution was not available. */
        val adjusted: Boolean,
    )

    private data class Candidate(
        val format: VideoFormat,
        val resolution: String,
        val number: Int,
        val distance: Int,
        val hasPreferredHost: Boolean,
        val targets: List<TargetLink>,
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
