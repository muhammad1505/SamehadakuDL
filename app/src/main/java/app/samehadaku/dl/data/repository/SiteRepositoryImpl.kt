package app.samehadaku.dl.data.repository

import app.samehadaku.dl.core.SiteMirrors
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.network.HtmlFetcher
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.parser.DownloadLinkParser
import app.samehadaku.dl.data.parser.AnimeDetailParser
import app.samehadaku.dl.data.parser.EpisodeListParser
import app.samehadaku.dl.data.parser.ReleaseScheduleParser
import app.samehadaku.dl.data.parser.SearchResultParser
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.AnimeDetail
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import app.samehadaku.dl.domain.repository.SiteRepository
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SiteRepositoryImpl @Inject constructor(
    private val fetcher: HtmlFetcher,
) : SiteRepository {

    private data class CachedLinks(val links: DownloadLinks, val savedAt: Long)

    private data class WorkingMirror(val url: String, val savedAt: Long)

    private val linkCache = ConcurrentHashMap<String, CachedLinks>()

    /** Episode page that last produced usable links when it was not the URL's own host. */
    private val mirrorCache = ConcurrentHashMap<String, WorkingMirror>()

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

        var page = fetcher.fetch(input, referer = UrlUtils.siteRoot(input))
        var episodes = EpisodeListParser.parse(page.html, page.finalUrl)
        if (episodes.isEmpty() && page.finalUrl != input) {
            // A WebView/redirect can return HTML whose final URL is a challenge or
            // tracking URL. Parse once against the URL the user actually selected so
            // relative episode links still resolve against the anime page.
            episodes = EpisodeListParser.parse(page.html, input)
        }

        // Search results can point to a mirror whose anime page is readable but stale
        // or rendered differently. Only try mirrors after parsing the selected URL
        // fails, and keep the original source URL for the saved subscription.
        if (episodes.isEmpty()) {
            for (mirror in SiteMirrors.candidates(input)) {
                val mirrorPage = runCatching {
                    fetcher.fetch(mirror, referer = UrlUtils.siteRoot(mirror))
                }.getOrNull() ?: continue
                val mirrorEpisodes = EpisodeListParser.parse(mirrorPage.html, mirrorPage.finalUrl)
                if (mirrorEpisodes.isNotEmpty()) {
                    page = mirrorPage
                    episodes = mirrorEpisodes
                    break
                }
            }
        }
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

    override suspend fun searchAnime(query: String): List<AnimeSearchResult> {
        val term = query.trim()
        if (term.length < MIN_SEARCH_LENGTH) return emptyList()

        var lastError: Exception? = null
        var verification: NeedsVerificationException? = null

        for (host in SiteMirrors.knownHosts().take(MAX_SEARCH_MIRRORS)) {
            val root = "https://$host/"
            val url = "$root?s=" + URLEncoder.encode(term, "UTF-8")
            try {
                val page = fetcher.fetch(url, referer = root)
                // A readable result page is authoritative, even when it has no cards.
                return SearchResultParser.parse(page.html, page.finalUrl, term)
            } catch (challenge: NeedsVerificationException) {
                verification = verification ?: challenge
            } catch (error: Exception) {
                Timber.d(error, "Search failed on %s", host)
                lastError = error
            }
        }

        verification?.let { throw it }
        lastError?.let { throw it }
        return emptyList()
    }

    override suspend fun loadAnimeDetail(url: String): AnimeDetail? {
        var lastError: Exception? = null
        for (source in listOf(url) + SiteMirrors.candidates(url)) {
            try {
                val page = fetcher.fetch(source, referer = UrlUtils.siteRoot(source))
                AnimeDetailParser.parse(org.jsoup.Jsoup.parse(page.html, page.finalUrl))?.let { return it }
            } catch (verification: NeedsVerificationException) {
                throw verification
            } catch (error: Exception) {
                lastError = lastError ?: error
                Timber.d(error, "Anime detail unavailable at %s", source)
            }
        }
        if (lastError != null) Timber.d(lastError, "Anime detail could not be parsed")
        return null
    }

    override suspend fun loadReleaseSchedule(): List<ReleaseScheduleItem> {
        var verification: NeedsVerificationException? = null
        val paths = listOf("/jadwal-rilis/", "/jadwal/")
        for (host in SiteMirrors.knownHosts().take(MAX_SCHEDULE_MIRRORS)) {
            for (path in paths) {
                val url = "https://$host$path"
                try {
                    val page = fetcher.fetch(url, referer = "https://$host/")
                    val parsed = ReleaseScheduleParser.parseSchedule(
                        org.jsoup.Jsoup.parse(page.html, page.finalUrl),
                        page.finalUrl,
                    )
                    if (parsed.isNotEmpty()) return parsed
                } catch (challenge: NeedsVerificationException) {
                    verification = verification ?: challenge
                } catch (error: Exception) {
                    Timber.d(error, "Schedule unavailable at %s", url)
                }
            }
        }
        verification?.let { throw it }
        return emptyList()
    }

    override suspend fun loadSiteAnnouncements(): List<String> {
        var verification: NeedsVerificationException? = null
        for (host in SiteMirrors.knownHosts().take(MAX_SCHEDULE_MIRRORS)) {
            val url = "https://$host/"
            try {
                val page = fetcher.fetch(url, referer = url)
                val parsed = ReleaseScheduleParser.parseAnnouncements(
                    org.jsoup.Jsoup.parse(page.html, page.finalUrl),
                )
                if (parsed.isNotEmpty()) return parsed
            } catch (challenge: NeedsVerificationException) {
                verification = verification ?: challenge
            } catch (error: Exception) {
                Timber.d(error, "Site announcement unavailable at %s", url)
            }
        }
        verification?.let { throw it }
        return emptyList()
    }

    /**
     * Reads the episode page from its own domain, remembering a mirror that already
     * served this episode. Mirrors are tried when the page cannot be read or carries no
     * download links at all.
     */
    override suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks? {
        val key = UrlUtils.normalizeUrl(episodeUrl)
        freshLinks(key)?.let { return it }

        var failure: Exception? = null
        var verification: NeedsVerificationException? = null

        for (source in sourceOrder(key, episodeUrl)) {
            val page = try {
                fetcher.fetchDownloadLinksPage(source)
            } catch (challenge: NeedsVerificationException) {
                // Only the URL the user gave us should send them to the verify screen.
                if (source == episodeUrl) verification = challenge
                continue
            } catch (error: Exception) {
                Timber.d(error, "Episode page unavailable at %s", source)
                failure = failure ?: error
                continue
            }
            val links = DownloadLinkParser.parse(page.html, page.finalUrl)
            if (links != null && !links.isEmpty) {
                return remember(key, episodeUrl, source, links)
            }
        }

        verification?.let { throw it }
        failure?.let { throw it }
        return null
    }

    override suspend fun loadMirrorLinks(episodeUrl: String): DownloadLinks? {
        val key = UrlUtils.normalizeUrl(episodeUrl)
        for (mirror in SiteMirrors.candidates(episodeUrl)) {
            val page = try {
                fetcher.fetchDownloadLinksPage(mirror)
            } catch (challenge: NeedsVerificationException) {
                continue
            } catch (error: Exception) {
                Timber.d(error, "Mirror page unavailable at %s", mirror)
                continue
            }
            val links = DownloadLinkParser.parse(page.html, page.finalUrl)
            if (links != null && !links.isEmpty) {
                Timber.d("Mirror %s served links for %s", mirror, episodeUrl)
                return remember(key, episodeUrl, mirror, links)
            }
        }
        return null
    }

    private fun sourceOrder(key: String, episodeUrl: String): List<String> = buildList {
        mirrorCache[key]?.let { mirror ->
            if (System.currentTimeMillis() - mirror.savedAt < CACHE_TTL_MS) add(mirror.url)
        }
        add(episodeUrl)
        addAll(SiteMirrors.candidates(episodeUrl))
    }.distinct()

    private fun freshLinks(key: String): DownloadLinks? {
        val cached = linkCache[key] ?: return null
        if (System.currentTimeMillis() - cached.savedAt >= CACHE_TTL_MS) return null
        return cached.links
    }

    private fun remember(
        key: String,
        episodeUrl: String,
        source: String,
        links: DownloadLinks,
    ): DownloadLinks {
        val now = System.currentTimeMillis()
        linkCache[key] = CachedLinks(links, now)
        if (source != episodeUrl) mirrorCache[key] = WorkingMirror(source, now)
        return links
    }

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_SEARCH_MIRRORS = 3
        const val MAX_SCHEDULE_MIRRORS = 3
    }
}
