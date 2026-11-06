package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Subscription
import javax.inject.Inject

data class SubscriptionCheck(
    val subscription: Subscription,
    val newEpisodes: List<Episode>,
)

data class SubscriptionFailure(
    val subscription: Subscription,
    val message: String,
)

data class SubscriptionCheckResult(
    val checks: List<SubscriptionCheck> = emptyList(),
    val upToDate: List<Subscription> = emptyList(),
    val failures: List<SubscriptionFailure> = emptyList(),
) {
    val newEpisodeCount: Int get() = checks.sumOf { it.newEpisodes.size }
    val hasAnything: Boolean get() = checks.isNotEmpty() || failures.isNotEmpty()
}

/**
 * Checks every followed anime for episodes newer than the recorded last episode,
 * mirroring the "Lanjutkan subscribe" mode of the Python downloader.
 */
class CheckSubscriptionsUseCase @Inject constructor(
    private val repository: SubscriptionRepository,
    private val detectEpisodes: DetectEpisodesUseCase,
) {

    suspend operator fun invoke(
        onProgress: (checked: Int, total: Int, animeName: String) -> Unit = { _, _, _ -> },
    ): SubscriptionCheckResult {
        val subscriptions = repository.currentSubscriptions()
        val checks = mutableListOf<SubscriptionCheck>()
        val upToDate = mutableListOf<Subscription>()
        val failures = mutableListOf<SubscriptionFailure>()

        subscriptions.forEachIndexed { index, subscription ->
            onProgress(index, subscriptions.size, subscription.name)
            try {
                val detection = detectEpisodes(subscription.animeUrl)
                val newEpisodes = detection.episodes
                    .filter { it.number > subscription.lastEpisode }
                    .distinctBy { it.url }
                    .sortedBy { it.number }
                if (newEpisodes.isEmpty()) {
                    upToDate.add(subscription)
                } else {
                    checks.add(SubscriptionCheck(subscription, newEpisodes))
                }
            } catch (verification: NeedsVerificationException) {
                throw verification
            } catch (error: Exception) {
                failures.add(
                    SubscriptionFailure(
                        subscription = subscription,
                        message = error.message ?: "Gagal memeriksa anime ini.",
                    ),
                )
            }
        }
        onProgress(subscriptions.size, subscriptions.size, "")
        return SubscriptionCheckResult(
            checks = checks.sortedBy { it.subscription.name.lowercase() },
            upToDate = upToDate,
            failures = failures,
        )
    }
}

/** Display name for a subscription; used by screens and import summaries. */
internal fun Subscription.displayName(): String =
    name.ifBlank { Names.fromSlug(slug.ifBlank { UrlUtils.lastPathSegment(animeUrl) }) }
