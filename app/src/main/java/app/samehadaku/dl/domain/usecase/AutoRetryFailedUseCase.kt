package app.samehadaku.dl.domain.usecase

import android.content.Context
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.download.DownloadService
import app.samehadaku.dl.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Re-queues downloads that failed because a host link was dead. Uploads on the file
 * hosts are usually refreshed hours or days later, so the same episode page is worth
 * another look instead of asking the user to remember to press retry.
 */
@Singleton
class AutoRetryFailedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val settings: SettingsRepository,
) {

    /** Returns how many downloads were queued again. */
    suspend operator fun invoke(): Int {
        if (!settings.currentFailedRetryEnabled()) return 0
        val retried = queue.retryFailedDue(MIN_AGE_MS, MAX_ATTEMPTS)
        if (retried > 0) {
            try {
                DownloadService.start(context)
            } catch (error: Exception) {
                // Android may refuse a foreground service from the background; the queue
                // keeps the tasks and picks them up the next time the app is opened.
                Timber.w(error, "Unable to start download service for automatic retry")
            }
        }
        return retried
    }

    companion object {
        /** Wait before retrying the same episode, so an early retry cannot loop. */
        const val MIN_AGE_MS = 12 * 60 * 60 * 1000L
        const val MAX_ATTEMPTS = 3
    }
}
