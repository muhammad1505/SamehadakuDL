package app.samehadaku.dl.data.smartstorage

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Registers the daily cleanup. WorkManager owns the schedule so it survives
 * reboots and app updates; a one-off catch-up run is also available for OEMs
 * that defer periodic work until the app is opened again.
 */
@Singleton
class SmartStorageScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmartStorageRepository,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /** Adds or cancels the periodic cleanup to match the saved opt-in setting. */
    suspend fun sync() {
        val enabled = runCatching { repository.currentSettings().enabled }.getOrDefault(false)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(NOW_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<SmartStorageWorker>(
            CLEANUP_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(Constraints.NONE)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.d("Smart Storage cleanup scheduled every %d hours", CLEANUP_INTERVAL_HOURS)
    }

    /**
     * Safety net for devices that freeze periodic work. Called when the app
     * becomes visible; it only enqueues a run when the last check is a day old.
     */
    suspend fun runNowIfDue() {
        val enabled = runCatching { repository.currentSettings().enabled }.getOrDefault(false)
        if (!enabled) return
        val lastCheck = runCatching { repository.currentLastCleanupAt() }.getOrDefault(0L)
        val intervalMillis = TimeUnit.HOURS.toMillis(CLEANUP_INTERVAL_HOURS)
        val due = lastCheck <= 0L || System.currentTimeMillis() - lastCheck >= intervalMillis
        if (!due) return

        val request = OneTimeWorkRequestBuilder<SmartStorageWorker>()
            .addTag(TAG)
            .addTag(NOW_TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val PERIODIC_WORK = "smart_storage_cleanup"
        const val NOW_WORK = "smart_storage_cleanup_now"
        private const val TAG = "smart-storage"
        private const val NOW_TAG = "smart-storage-now"
        private const val CLEANUP_INTERVAL_HOURS = 24L
    }
}
