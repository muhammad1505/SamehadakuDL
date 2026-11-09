package app.samehadaku.dl.domain.repository

import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.AnimeDetail
import app.samehadaku.dl.domain.model.ReleaseScheduleItem

interface SiteRepository {
    suspend fun detectEpisodes(url: String): DetectionResult

    /**
     * Searches the site (`/?s=`) and returns the anime cards on the result page.
     * Mirrors are tried when the first host cannot be read.
     */
    suspend fun searchAnime(query: String): List<AnimeSearchResult>
    suspend fun loadAnimeDetail(url: String): AnimeDetail?
    suspend fun loadReleaseSchedule(): List<ReleaseScheduleItem>
    suspend fun loadSiteAnnouncements(): List<String>
    suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks?

    /**
     * Reads the episode page from the other Samehadaku mirrors, ignoring the URL's own
     * host, and caches the first result under [episodeUrl] so the download itself uses
     * those links. Returns null when no mirror serves usable links.
     */
    suspend fun loadMirrorLinks(episodeUrl: String): DownloadLinks?
}
