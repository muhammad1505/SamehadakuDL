package app.samehadaku.dl.domain.repository

import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.DownloadLinks

interface SiteRepository {
    suspend fun detectEpisodes(url: String): DetectionResult
    suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks?
}
