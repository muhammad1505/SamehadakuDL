package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.repository.SiteRepository
import javax.inject.Inject

class DetectEpisodesUseCase @Inject constructor(
    private val repository: SiteRepository,
) {
    suspend operator fun invoke(url: String): DetectionResult = repository.detectEpisodes(url)
}
