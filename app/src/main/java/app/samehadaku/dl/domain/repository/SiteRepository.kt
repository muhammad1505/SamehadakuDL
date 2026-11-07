package app.samehadaku.dl.domain.repository

import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.DownloadLinks

interface SiteRepository {
    suspend fun detectEpisodes(url: String): DetectionResult
    suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks?

    /**
     * Reads the episode page from the other Samehadaku mirrors, ignoring the URL's own
     * host, and caches the first result under [episodeUrl] so the download itself uses
     * those links. Returns null when no mirror serves usable links.
     */
    suspend fun loadMirrorLinks(episodeUrl: String): DownloadLinks?
}
