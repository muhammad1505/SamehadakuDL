package app.samehadaku.dl.data.smartstorage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.samehadaku.dl.domain.usecase.CleanWatchedFilesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmartStorageEntryPoint {
    fun smartStorageRepository(): SmartStorageRepository
    fun cleanWatchedFiles(): CleanWatchedFilesUseCase
    fun smartStorageNotifier(): SmartStorageNotifier
}

/**
 * Background cleanup. The worker never launches a system delete confirmation
 * because it has no activity; files that need one keep their watched marker and
 * are reported through the app log.
 */
class SmartStorageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            SmartStorageEntryPoint::class.java,
        )
        val settings = entry.smartStorageRepository().currentSettings()
        if (!settings.enabled) return Result.success()

        return try {
            val result = entry.cleanWatchedFiles().invoke()
            Timber.d(
                "Smart Storage cleanup deleted=%d reclaimed=%d skipped=%d blocked=%d waiting=%d",
                result.deletedFiles,
                result.reclaimedBytes,
                result.skippedFiles,
                result.blockedFiles,
                result.waitingFiles,
            )
            runCatching { entry.smartStorageNotifier().notifyCleanup(result) }
                .onFailure { Timber.w(it, "Smart Storage notification failed") }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Local storage errors are usually transient; the daily schedule and
            // the foreground catch-up will try again without a tight retry loop.
            Timber.w(error, "Smart Storage cleanup failed")
            Result.success()
        }
    }
}
