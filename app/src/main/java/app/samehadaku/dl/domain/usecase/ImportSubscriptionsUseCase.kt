package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.data.subscriptions.ImportSummary
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import javax.inject.Inject

/** Imports the Python downloader's `.samehadaku_subs.json`. */
class ImportSubscriptionsUseCase @Inject constructor(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(json: String): ImportSummary = repository.importFromScript(json)
}
