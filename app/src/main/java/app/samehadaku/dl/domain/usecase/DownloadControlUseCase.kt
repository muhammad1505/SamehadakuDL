package app.samehadaku.dl.domain.usecase

import android.content.Context
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.download.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class DownloadControlUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
) {

    fun pauseAll() {
        queue.pauseAll()
    }

    fun resumeAll() {
        queue.resumeAll()
        startServiceSafely()
    }

    fun cancelAll() {
        queue.cancelAll()
    }

    fun cancel(id: String) {
        queue.cancel(id)
    }

    fun pause(id: String) {
        queue.pause(id)
    }

    fun resume(id: String) {
        queue.resume(id)
        startServiceSafely()
    }

    fun remove(id: String) {
        queue.remove(id)
    }

    fun retry(id: String) {
        queue.retry(id)
        startServiceSafely()
    }

    fun retryFailed() {
        queue.retryFailed()
        startServiceSafely()
    }

    fun clearFinished() {
        queue.clearFinished()
    }

    private fun startServiceSafely() {
        try {
            DownloadService.start(context)
        } catch (error: Exception) {
            Timber.w(error, "Unable to start download service")
        }
    }
}
