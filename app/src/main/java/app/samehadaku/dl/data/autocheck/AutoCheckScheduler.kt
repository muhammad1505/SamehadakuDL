package app.samehadaku.dl.data.autocheck

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.samehadaku.dl.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Registers the periodic background check with WorkManager. WorkManager owns the
 * schedule, so it survives app kills, reboots and app updates; the OS may defer
 * runs while in Doze, which is expected for periodic work.
 */
@Singleton
class AutoCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Adds, updates or cancels the periodic check to match the saved settings. */
    suspend fun sync() {
        val enabled = runCatching { settings.currentAutoCheckEnabled() }.getOrDefault(true)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(NOW_WORK)
            return
        }
        val minutes = runCatching { settings.currentAutoCheckIntervalMinutes() }
            .getOrDefault(DEFAULT_INTERVAL_MINUTES)
            .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

        val alreadyScheduled = runCatching {
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK).first().any { info ->
                !info.state.isFinished && intervalTag(minutes) in info.tags
            }
        }.getOrDefault(false)
        if (alreadyScheduled) return

        val request = PeriodicWorkRequestBuilder<AutoCheckWorker>(
            minutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints)
            .addTag(TAG)
            .addTag(intervalTag(minutes))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
        Timber.d("Auto-check scheduled every %d minutes", minutes)
    }

    /**
     * One-off run used by the "Cek sekarang" button; replaces a pending manual run.
     * Expedited so the check starts immediately instead of waiting for a maintenance
     * window, which is why it previously looked like nothing happened until later.
     */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<AutoCheckWorker>()
            .setConstraints(networkConstraints)
            .addTag(TAG)
            .addTag(NOW_TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Safety net for OEMs that freeze background work: if the last check is older
     * than the configured interval, run once when the app is opened.
     */
    suspend fun runNowIfDue() {
        val enabled = runCatching { settings.currentAutoCheckEnabled() }.getOrDefault(true)
        if (!enabled) return
        val minutes = runCatching { settings.currentAutoCheckIntervalMinutes() }
            .getOrDefault(DEFAULT_INTERVAL_MINUTES)
            .coerceAtLeast(MIN_INTERVAL_MINUTES)
        val lastCheck = runCatching { settings.currentLastAutoCheckAt() }.getOrDefault(0L)
        val intervalMillis = TimeUnit.MINUTES.toMillis(minutes.toLong())
        val due = lastCheck <= 0L || System.currentTimeMillis() - lastCheck >= intervalMillis
        if (due) runNow()
    }

    private fun intervalTag(minutes: Int) = "auto-check-${minutes}m"

    companion object {
        const val PERIODIC_WORK = "auto_check_subscriptions"
        const val NOW_WORK = "auto_check_subscriptions_now"
        private const val TAG = "auto-check"
        private const val NOW_TAG = "auto-check-now"
        private const val DEFAULT_INTERVAL_MINUTES = 60
        private const val MIN_INTERVAL_MINUTES = 15
        private const val MAX_INTERVAL_MINUTES = 24 * 60
    }
}
