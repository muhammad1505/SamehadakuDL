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
     * Reads the episode page from every Samehadaku mirror and returns each distinct
     * link set that actually parsed. The URL's own page is included as a last resort
     * because the richer canonical page can be dead while the mirror still serves a
     * file. A parseable page is not proof that its uploads still work, so callers must
     * try the sets in order until one yields a live target. Returns an empty list when
     * no page works.
     */
    suspend fun loadMirrorLinks(episodeUrl: String): List<DownloadLinks>
}
