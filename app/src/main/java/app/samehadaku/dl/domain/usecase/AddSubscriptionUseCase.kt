package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.Subscription
import javax.inject.Inject

/**
 * Follows an anime page. Only list pages (`/anime/<slug>/`) make sense here;
 * a single episode URL has no episode list to watch for updates.
 */
class AddSubscriptionUseCase @Inject constructor(
    private val detectEpisodes: DetectEpisodesUseCase,
    private val subscriptions: SubscriptionRepository,
) {

    suspend operator fun invoke(url: String, lastEpisode: Int = 0): Subscription {
        val detection = detectEpisodes(url)
        if (!detection.isListPage || detection.episodes.size <= 1) {
            throw FetchFailureException(
                "Tempel URL halaman anime (contoh: .../anime/judul/), bukan URL satu episode.",
            )
        }
        val slug = detection.animeSlug.ifBlank { UrlUtils.lastPathSegment(detection.finalUrl) }
        if (slug.isBlank()) {
            throw FetchFailureException("Slug anime tidak bisa dibaca dari URL ini.")
        }
        val subscription = Subscription(
            name = Names.fromSlug(slug),
            animeUrl = UrlUtils.normalizeUrl(detection.finalUrl),
            slug = slug,
            lastEpisode = lastEpisode.coerceAtLeast(0),
        )
        subscriptions.addOrUpdate(subscription)
        return subscription
    }
}
