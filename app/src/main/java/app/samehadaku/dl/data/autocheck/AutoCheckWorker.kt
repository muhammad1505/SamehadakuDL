package app.samehadaku.dl.data.autocheck

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.network.NetworkUnavailableException
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.usecase.CheckSubscriptionsUseCase
import app.samehadaku.dl.domain.usecase.EnqueueDownloadsUseCase
import app.samehadaku.dl.domain.usecase.LoadQualityUseCase
import app.samehadaku.dl.domain.usecase.SubscriptionCheckResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoCheckEntryPoint {
    fun checkSubscriptions(): CheckSubscriptionsUseCase
    fun settings(): SettingsRepository
    fun cloudflareSession(): CloudflareSession
    fun notifier(): AutoCheckNotifier
    fun enqueueDownloads(): EnqueueDownloadsUseCase
    fun loadQuality(): LoadQualityUseCase
    fun downloadQueue(): DownloadQueue
}

/**
 * Background worker: checks followed anime for newer episodes, queues them for
 * download using the remembered quality, and posts a notification that opens
 * the download queue. If no quality is known yet, it only notifies and opens
 * Langganan so the user can pick one.
 */
class AutoCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoCheckEntryPoint::class.java,
        )
        val settings = entry.settings()
        if (!settings.currentAutoCheckEnabled()) return Result.success()

        // Cloudflare cookies live in DataStore; restore them for a cold process.
        runCatching { entry.cloudflareSession().restore() }

        return try {
            val result = entry.checkSubscriptions().invoke()
            settings.setLastAutoCheckAt(System.currentTimeMillis())
            if (result.checks.isEmpty()) return Result.success()

            val quality = resolveQuality(entry, result)
            if (quality == null) {
                // No remembered quality yet: let the user choose on Langganan.
                entry.notifier().notifyNewEpisodes(result)
                return Result.success()
            }
            enqueueNewEpisodes(entry, result, quality)
            Result.success()
        } catch (cancellation: CancellationException) {
            // WorkManager stops the worker when its execution window ends or when
            // the request is replaced. This is not a failure and must not be
            // swallowed, otherwise the app logs a misleading "Auto check failed".
            throw cancellation
        } catch (verification: NeedsVerificationException) {
            Timber.d(verification, "Auto check needs Cloudflare verification")
            settings.setLastAutoCheckAt(System.currentTimeMillis())
            entry.notifier().notifyVerificationNeeded()
            Result.success()
        } catch (offline: NetworkUnavailableException) {
            // The check aborted because no known host could be resolved. Leave the
            // last-check timestamp alone so the next run/app open retries sooner.
            Timber.w("Auto check skipped: %s", offline.message)
            Result.success()
        } catch (error: Exception) {
            // Per-anime failures are already collected by the use case; this only
            // catches setup errors. Do not retry aggressively in the background.
            Timber.w(error, "Auto check failed")
            Result.success()
        }
    }

    private suspend fun resolveQuality(
        entry: AutoCheckEntryPoint,
        result: SubscriptionCheckResult,
    ): QualityPreference? {
        val settings = entry.settings()
        settings.currentQualityPreference()?.let { return it }

        val sample = result.checks.firstOrNull()?.newEpisodes?.firstOrNull() ?: return null
        return try {
            val preview = entry.loadQuality().invoke(listOf(sample))
            val format = preview.links.formats().firstOrNull() ?: return null
            val resolution = preview.links.resolutions(format).firstOrNull() ?: return null
            val host = preview.links.hosts(format, resolution).firstOrNull() ?: return null
            val preference = QualityPreference(format, resolution, host)
            settings.saveQualityPreference(format, resolution, host)
            preference
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (verification: NeedsVerificationException) {
            throw verification
        } catch (error: Exception) {
            Timber.w(error, "Cannot resolve a quality for auto download")
            null
        }
    }

    private suspend fun enqueueNewEpisodes(
        entry: AutoCheckEntryPoint,
        result: SubscriptionCheckResult,
        quality: QualityPreference,
    ) {
        val enqueue = entry.enqueueDownloads()
        val alreadyQueued = entry.downloadQueue().tasks.value
            .map { task -> task.episodeUrl }
            .toMutableSet()
        var queued = 0
        var skipped = 0

        for (check in result.checks) {
            val fresh = check.newEpisodes.filterNot { episode -> episode.url in alreadyQueued }
            skipped += check.newEpisodes.size - fresh.size
            if (fresh.isEmpty()) continue
            try {
                queued += enqueue(
                    episodes = fresh,
                    animeSlug = check.subscription.slug,
                    format = quality.format,
                    resolution = quality.resolution,
                    preferredHost = quality.host,
                ).queued
                alreadyQueued += fresh.map { episode -> episode.url }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (verification: NeedsVerificationException) {
                // The preflight hit Cloudflare; the caller turns this into a notification.
                throw verification
            } catch (error: Exception) {
                Timber.w(error, "Failed to queue %s for download", check.subscription.name)
            }
        }

        if (queued > 0) {
            entry.notifier().notifyDownloadsQueued(result, queued, skipped, quality)
        }
    }
}
