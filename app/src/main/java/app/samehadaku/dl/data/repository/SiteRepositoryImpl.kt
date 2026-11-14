package app.samehadaku.dl.data.repository

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.SiteMirrors
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.network.FetchedPage
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
            // Mirrors often use a different anime slug, which host swapping cannot
            // guess. Search each mirror by the humanized slug so a moved series is
            // still detectable from an old `.care`/`.how` link.
            val slugGuess = UrlUtils.lastPathSegment(input)
            if (slugGuess.isNotBlank()) {
                for (host in SiteMirrors.knownHosts()) {
                    val mirrorPage = animePageBySearch(host, slugGuess) ?: continue
                    val mirrorEpisodes = EpisodeListParser.parse(
                        mirrorPage.html,
                        mirrorPage.finalUrl,
                    )
                    if (mirrorEpisodes.isNotEmpty()) {
                        page = mirrorPage
                        episodes = mirrorEpisodes
                        break
                    }
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
                val results = SearchResultParser.parse(page.html, page.finalUrl, term)
                // A readable page with zero cards is often a parked/placeholder
                // mirror or a temporary empty search response. Keep trying the
                // remaining hosts instead of letting one bad mirror hide the site.
                if (results.isNotEmpty()) return results
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

        // Slug differences (`ao-ashi-s2` vs `ao-ashi-season-2`) make host swapping
        // fail even though the anime exists on the mirror. Search for it there.
        val slug = UrlUtils.lastPathSegment(url)
        if (slug.isNotBlank()) {
            for (host in SiteMirrors.knownHosts()) {
                val page = animePageBySearch(host, slug) ?: continue
                try {
                    val detail = AnimeDetailParser.parse(
                        org.jsoup.Jsoup.parse(page.html, page.finalUrl),
                    )
                    if (detail != null) return detail
                } catch (error: Exception) {
                    lastError = lastError ?: error
                    Timber.d(error, "Anime detail unavailable at %s", page.finalUrl)
                }
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
     *
     * `.li` pages often list fewer servers (sometimes only Gofile) than the canonical
     * `.how` page for the same episode. When the input URL is not on a primary host,
     * every adapted primary candidate is read too and the richest link set wins, so
     * the quality screen shows the same hosts regardless of which mirror the user
     * copied the URL from.
     */
    override suspend fun loadDownloadLinks(episodeUrl: String): DownloadLinks? {
        val key = UrlUtils.normalizeUrl(episodeUrl)
        freshLinks(key)?.let { return it }

        val ownHost = UrlUtils.hostOf(episodeUrl)
        val ownIsPrimary = SiteMirrors.isPrimaryHost(ownHost)

        var failure: Exception? = null
        var verification: NeedsVerificationException? = null
        var best: DownloadLinks? = null
        var bestSource: String? = null

        suspend fun trySource(source: String) {
            val page = try {
                fetcher.fetchDownloadLinksPage(source)
            } catch (challenge: NeedsVerificationException) {
                // Only the URL the user gave us should send them to the verify screen.
                if (source == episodeUrl) verification = challenge
                return
            } catch (error: Exception) {
                Timber.d(error, "Episode page unavailable at %s", source)
                failure = failure ?: error
                return
            }
            val links = DownloadLinkParser.parse(page.html, page.finalUrl)
            if (links == null || links.isEmpty) return
            if (best == null || links.weight() > best!!.weight()) {
                best = links
                bestSource = source
            }
        }

        for (source in sourceOrder(key, episodeUrl)) {
            trySource(source)
            // The primary family is authoritative and all of its hosts share the
            // same content; once one succeeds there is nothing richer to find.
            if (best != null && SiteMirrors.isPrimaryHost(UrlUtils.hostOf(source))) break
        }

        val bestHost = bestSource?.let { UrlUtils.hostOf(it) }
        if (!ownIsPrimary && (best == null || !SiteMirrors.isPrimaryHost(bestHost))) {
            // Both adapted primary paths failed, usually because this anime uses a
            // different slug there. Find the episode through the mirror's search.
            for (source in searchDerivedEpisodeUrls(
                episodeUrl = episodeUrl,
                hosts = SiteMirrors.primaryHosts(),
            )) {
                trySource(source)
            }
        }

        best?.let { links ->
            val served = bestSource ?: episodeUrl
            return remember(key, episodeUrl, served, links.copy(sourceUrl = served))
        }
        verification?.let { throw it }
        failure?.let { throw it }
        return null
    }

    /**
     * Every usable link set the other Samehadaku mirrors expose for this episode.
     * A parseable page is not enough: callers still scan each set for a live target,
     * because a placeholder mirror can return HTML and `.li` may only carry Gofile.
     */
    override suspend fun loadMirrorLinks(episodeUrl: String): List<DownloadLinks> {
        val results = mutableListOf<DownloadLinks>()
        val tried = linkedSetOf<String>()
        val coveredHosts = linkedSetOf<String>()

        suspend fun tryUrls(urls: Iterable<String>) {
            for (url in urls) {
                if (!tried.add(UrlUtils.normalizeUrl(url))) continue
                val page = try {
                    fetcher.fetchDownloadLinksPage(url)
                } catch (challenge: NeedsVerificationException) {
                    continue
                } catch (error: Exception) {
                    Timber.d(error, "Mirror page unavailable at %s", url)
                    continue
                }
                val links = DownloadLinkParser.parse(page.html, page.finalUrl)
                if (links == null || links.isEmpty) continue
                UrlUtils.hostOf(url)?.let { coveredHosts += it }
                if (results.any { it.data == links.data }) continue
                Timber.d("Mirror %s served links for %s", url, episodeUrl)
                // Keep the page that actually served the links so the queue can
                // report which mirror was used instead of a generic message.
                results += links.copy(sourceUrl = page.finalUrl)
            }
        }

        tryUrls(SiteMirrors.candidates(episodeUrl))
        // The original page is a valid last resort: the canonical page may be the
        // richer one, but it can also be the dead one while `.li` still serves a file.
        tryUrls(listOf(episodeUrl))
        // Slug differences cannot be guessed by swapping hosts. Search the mirrors
        // whose direct episode URL did not parse, since those are the ones where the
        // anime slug is likely different.
        val ownHost = UrlUtils.hostOf(episodeUrl)
        tryUrls(
            searchDerivedEpisodeUrls(
                episodeUrl = episodeUrl,
                excludeHosts = coveredHosts + setOfNotNull(ownHost),
            ),
        )

        return results
    }

    private fun sourceOrder(key: String, episodeUrl: String): List<String> = buildList {
        mirrorCache[key]?.let { mirror ->
            if (System.currentTimeMillis() - mirror.savedAt < CACHE_TTL_MS) add(mirror.url)
        }
        val candidates = SiteMirrors.candidates(episodeUrl)
        if (SiteMirrors.isPrimaryHost(UrlUtils.hostOf(episodeUrl))) {
            add(episodeUrl)
            addAll(candidates)
        } else {
            // Prefer the richer canonical page over a sparse `.li`/legacy page.
            addAll(candidates)
            add(episodeUrl)
        }
    }.distinctBy { UrlUtils.normalizeUrl(it) }

    private suspend fun searchDerivedEpisodeUrls(
        episodeUrl: String,
        hosts: List<String> = SiteMirrors.knownHosts(),
        excludeHosts: Set<String> = emptySet(),
    ): List<String> {
        val ref = SiteMirrors.episodeRef(episodeUrl) ?: return emptyList()
        val urls = linkedSetOf<String>()
        for (host in hosts) {
            if (excludeHosts.any { it.equals(host, ignoreCase = true) }) continue
            val animePage = animePageBySearch(host, ref.animeSlug) ?: continue
            val episodes = EpisodeListParser.parse(animePage.html, animePage.finalUrl)
            val match = episodes.firstOrNull { it.number == ref.number } ?: continue
            urls += match.url
        }
        return urls.toList()
    }

    private suspend fun animePageBySearch(host: String, animeSlug: String): FetchedPage? {
        val term = Names.fromSlug(animeSlug)
        if (term.isBlank()) return null
        val results = searchOnHost(host, term)
        val best = results.firstOrNull() ?: return null
        return runCatching {
            fetcher.fetch(best.url, referer = "https://$host/")
        }.getOrNull()
    }

    private suspend fun searchOnHost(host: String, term: String): List<AnimeSearchResult> {
        val root = "https://$host/"
        val url = "$root?s=" + URLEncoder.encode(term, "UTF-8")
        val page = try {
            fetcher.fetch(url, referer = root)
        } catch (error: Exception) {
            Timber.d(error, "Search failed on %s", host)
            return emptyList()
        }
        return runCatching {
            SearchResultParser.parse(page.html, page.finalUrl, term)
        }.getOrDefault(emptyList())
    }

    private fun DownloadLinks.weight(): Int =
        data.values.sumOf { resolutions -> resolutions.values.sumOf { it.size } }

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
        if (UrlUtils.normalizeUrl(source) != key) {
            mirrorCache[key] = WorkingMirror(source, now)
        } else {
            // The main domain works again; stop sending new attempts to a stale mirror.
            mirrorCache.remove(key)
        }
        return links
    }

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_SEARCH_MIRRORS = 3
        const val MAX_SCHEDULE_MIRRORS = 3
    }
}
