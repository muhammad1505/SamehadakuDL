package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.repository.SiteRepository
import javax.inject.Inject

data class QualityPreview(
    val episodeUrl: String,
    val links: DownloadLinks,
)

/**
 * Quality preview uses the first episode (in the selected list) that returns any links.
 */
class LoadQualityUseCase @Inject constructor(
    private val repository: SiteRepository,
) {

    suspend operator fun invoke(episodes: List<Episode>): QualityPreview {
        if (episodes.isEmpty()) {
            throw FetchFailureException("Tidak ada episode yang dipilih")
        }

        var lastError: Exception? = null
        for (episode in episodes) {
            try {
                val links = repository.loadDownloadLinks(episode.url)
                if (links != null && !links.isEmpty) {
                    return QualityPreview(episode.url, links)
                }
            } catch (error: NeedsVerificationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw FetchFailureException(
            "Tidak ada tautan unduhan yang tersedia untuk episode terpilih. " +
                "Episode mungkin belum rilis.",
            lastError,
        )
    }
}
