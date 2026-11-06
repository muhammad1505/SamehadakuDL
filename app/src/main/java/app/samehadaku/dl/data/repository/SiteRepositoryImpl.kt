package app.samehadaku.dl.data.repository

import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.network.HtmlFetcher
import app.samehadaku.dl.data.parser.DownloadLinkParser
import app.samehadaku.dl.data.parser.EpisodeListParser
import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.repository.SiteRepository
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteRepositoryImpl @Inject constructor(
    private val fetcher: HtmlFetcher,
) : SiteRepository {

    private data class CachedLinks(val links: DownloadLinks, val savedAt: Long)

    private val linkCache = ConcurrentHashMap<String, CachedLinks>()

    override suspend fun detectEpisodes(url: String): DetectionResult {
        val input = UrlUtils.normalizeInput(url)
        if (!UrlUtils.isHttpUrl(input)) {
            throw FetchFailureException("URL tidak valid. Tempel tautan http(s) dari Samehadaku.")
        }

        if (!UrlUtils.isListUrl(input)) {
            val slug = UrlUtils.lastPathSegment(input)
            if (slug.isBlank()) throw FetchFailureException("URL episode tidak valid")
            return DetectionResult(
                sourceUrl = input,
                finalUrl = input,
                animeSlug = slug,
                episodes = listOf(Episode(number = 1, url = input, slug = slug)),
                isListPage = false,
            )
        }

        val page = fetcher.fetch(input, referer = UrlUtils.siteRoot(input))
        val episodes = EpisodeListParser.parse(page.html, page.finalUrl)
        if (episodes.isEmpty()) {
            throw FetchFailureException(
                "Tidak ada episode yang terdeteksi di halaman ini. " +
                    "Pastikan URL halaman anime benar.",
            )
        }

        val animeSlug = try {
            UrlUtils.lastPathSegment(URI(page.finalUrl).path.orEmpty())
        } catch (_: Exception) {
            UrlUtils.lastPathSegment(input)
        }

        return DetectionResult(
            sourceUrl = input,
            finalUrl = page.finalUrl,
            animeSlug = animeSlug,
            episodes = episodes,
            isListPage = true,
        )
    }

    override suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks? {
        val key = UrlUtils.normalizeUrl(episodeUrl)
        val cached = linkCache[key]
        if (cached != null && System.currentTimeMillis() - cached.savedAt < CACHE_TTL_MS) {
            return cached.links
        }

        val page = fetcher.fetchDownloadLinksPage(episodeUrl)
        val links = DownloadLinkParser.parse(page.html, page.finalUrl)
        if (links != null && !links.isEmpty) {
            linkCache[key] = CachedLinks(links, System.currentTimeMillis())
        }
        return links
    }

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
    }
}
