package app.samehadaku.dl.domain.usecase

import android.content.Context
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.download.DownloadService
import app.samehadaku.dl.data.download.LinkPreflight
import app.samehadaku.dl.data.download.PreflightResult
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.EnqueueResult
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

/**
 * Queues the selected episodes for download. Each episode is checked up front so a
 * dead host link is reported right away instead of after a failed download; those
 * episodes are queued as skipped so they still show up in the queue and can be
 * retried by hand or by [AutoRetryFailedUseCase].
 */
@Singleton
class EnqueueDownloadsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val preflight: LinkPreflight,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(
        episodes: List<Episode>,
        animeSlug: String,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): EnqueueResult {
        if (episodes.isEmpty()) {
            return EnqueueResult(total = 0, queued = 0, deadEpisodes = emptyList())
        }
        val tasks = episodes.map { episode ->
            DownloadTask.create(
                episode = episode,
                animeSlug = animeSlug,
                format = format,
                resolution = resolution,
                preferredHost = preferredHost,
            )
        }

        val allowDowngrade = runCatching { settings.currentQualityDowngrade() }
            .getOrDefault(false)
        val checked = coroutineScope {
            val gate = Semaphore(PREFLIGHT_CONCURRENCY)
            tasks.map { task ->
                async(Dispatchers.IO) {
                    gate.withPermit { task to evaluate(task, allowDowngrade) }
                }
            }.awaitAll()
        }
        val checkedAt = System.currentTimeMillis()
        val prepared = checked.map { (task, result) ->
            when (result) {
                is PreflightResult.Dead -> task.copy(
                    state = DownloadState.SKIPPED,
                    failureReasons = listOf(
                        "Link mati di domain utama dan mirror: " +
                            result.reasons.distinct().joinToString("; "),
                    ),
                    note = "Tidak diunduh sekarang. Akan dicoba ulang otomatis, " +
                        "atau tekan Ulangi untuk mencoba langsung.",
                    finishedAt = checkedAt,
                )

                PreflightResult.Ready, PreflightResult.Unknown -> task
            }
        }

        val dead = prepared.filter { it.state == DownloadState.SKIPPED }
        queue.enqueue(prepared)
        if (dead.size < prepared.size) startServiceSafely()

        return EnqueueResult(
            total = prepared.size,
            queued = prepared.size - dead.size,
            deadEpisodes = dead.map { it.episodeNumber }.sorted(),
        )
    }

    private suspend fun evaluate(
        task: DownloadTask,
        allowDowngrade: Boolean,
    ): PreflightResult = try {
        preflight.check(task, allowDowngrade)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (verification: NeedsVerificationException) {
        // The site is blocked for every episode, so the UI has to verify first.
        throw verification
    } catch (error: Exception) {
        // A page that cannot be read is not a dead link; let the queue decide.
        Timber.d(error, "Preflight skipped for %s", task.episodeUrl)
        PreflightResult.Unknown
    }

    private fun startServiceSafely() {
        try {
            DownloadService.start(context)
        } catch (error: Exception) {
            Timber.w(error, "Unable to start download service")
        }
    }

    private companion object {
        const val PREFLIGHT_CONCURRENCY = 3
    }
}
