package app.samehadaku.dl.domain.usecase

import android.content.Context
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.download.DownloadService
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class EnqueueDownloadsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
) {

    suspend operator fun invoke(
        episodes: List<Episode>,
        animeSlug: String,
        format: VideoFormat,
        resolution: String,
        preferredHost: Host,
    ): Int {
        if (episodes.isEmpty()) return 0
        val tasks = episodes.map { episode ->
            DownloadTask.create(
                episode = episode,
                animeSlug = animeSlug,
                format = format,
                resolution = resolution,
                preferredHost = preferredHost,
            )
        }
        queue.enqueue(tasks)
        startServiceSafely()
        return tasks.size
    }

    private fun startServiceSafely() {
        try {
            DownloadService.start(context)
        } catch (error: Exception) {
            Timber.w(error, "Unable to start download service")
        }
    }
}
