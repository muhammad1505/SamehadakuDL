package app.samehadaku.dl.data.download

import app.samehadaku.dl.data.host.LinkHealth
import app.samehadaku.dl.data.host.LinkHealthChecker
import app.samehadaku.dl.data.parser.HostSelector
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.TargetLink
import app.samehadaku.dl.domain.repository.SiteRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Outcome of checking every candidate link of one episode before it is queued. */
sealed interface PreflightResult {
    /** At least one host answered with a real file. */
    data object Ready : PreflightResult
    /** No link could be verified (network errors, nothing parsed, ...); download must try. */
    data object Unknown : PreflightResult
    /** Every candidate link answered definitively that the file is gone. */
    data class Dead(val reasons: List<String>) : PreflightResult
}

/**
 * Cheap up-front check of the links an episode would download from. The episode page
 * is read through [SiteRepository], which caches it for the download itself, so the
 * preflight mostly reuses work instead of adding a round trip.
 *
 * When every link on the episode's own domain is dead, the same episode is looked up on
 * the other Samehadaku mirrors: they often point at a fresh upload for an episode whose
 * files were taken down.
 */
@Singleton
class LinkPreflight @Inject constructor(
    private val siteRepository: SiteRepository,
    private val checker: LinkHealthChecker,
) {

    suspend fun check(task: DownloadTask, allowDowngrade: Boolean): PreflightResult {
        val links = siteRepository.loadDownloadLinks(task.episodeUrl)
            ?: return PreflightResult.Unknown

        return when (val main = scan(task, allowDowngrade, links)) {
            is PreflightResult.Dead -> scanMirrors(task, allowDowngrade, main, links)
            PreflightResult.Ready, PreflightResult.Unknown -> main
        }
    }

    private suspend fun scanMirrors(
        task: DownloadTask,
        allowDowngrade: Boolean,
        main: PreflightResult.Dead,
        mainLinks: DownloadLinks,
    ): PreflightResult {
        val mirrorSets = runCatching { siteRepository.loadMirrorLinks(task.episodeUrl) }
            .getOrDefault(emptyList())
        if (mirrorSets.isEmpty()) return main

        val reasons = main.reasons.toMutableList()
        var undecided = false
        for (mirrorLinks in mirrorSets) {
            // The original page is returned as a last resort; do not probe it twice.
            if (mirrorLinks.data == mainLinks.data) continue
            when (val mirror = scan(task, allowDowngrade, mirrorLinks)) {
                PreflightResult.Ready -> return PreflightResult.Ready
                is PreflightResult.Dead -> reasons += mirror.reasons
                PreflightResult.Unknown -> undecided = true
            }
        }
        return if (undecided) {
            PreflightResult.Unknown
        } else {
            PreflightResult.Dead(reasons.distinct())
        }
    }

    private suspend fun scan(
        task: DownloadTask,
        allowDowngrade: Boolean,
        links: DownloadLinks,
    ): PreflightResult {
        val targets = targetsFor(task, allowDowngrade, links)
        if (targets.isEmpty()) {
            return PreflightResult.Dead(
                listOf("kualitas ${task.resolution} tidak tersedia di semua server"),
            )
        }

        val reasons = mutableListOf<String>()
        var undecided = false
        for (target in targets) {
            currentCoroutineContext().ensureActive()
            when (val health = checker.check(target)) {
                LinkHealth.Alive -> return PreflightResult.Ready
                is LinkHealth.Dead -> reasons += health.reason
                LinkHealth.Unknown -> undecided = true
            }
        }
        return if (undecided) PreflightResult.Unknown else PreflightResult.Dead(reasons)
    }

    private fun targetsFor(
        task: DownloadTask,
        allowDowngrade: Boolean,
        links: DownloadLinks,
    ): List<TargetLink> {
        val selection = HostSelector.findBestTargets(
            links = links,
            format = task.format,
            resolution = task.resolution,
            preferredHost = task.preferredHost,
        ) ?: return emptyList()
        if (selection.adjusted && !allowDowngrade) return emptyList()
        return selection.targets
    }
}
