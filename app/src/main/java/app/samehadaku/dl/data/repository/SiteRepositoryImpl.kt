package app.samehadaku.dl.data.repository

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.SiteMirrors
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.FetchFailureException
import app.samehadaku.dl.data.network.FetchedPage
import app.samehadaku.dl.data.network.HtmlFetcher
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.network.NetworkUnavailableException
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
import kotlinx.coroutines.CancellationException
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
        Timber.i("Detect episodes from %s", input)
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

        var page: FetchedPage? = null
        var episodes: List<Episode> = emptyList()
        var lastError: Exception? = null
        var anyPageLoaded = false

        // Read the selected page, then the known mirrors. A mirror is useful not only
        // when the selected page parses to nothing, but also when that host itself is
        // unreachable (DNS failure, HTTP 5xx, ...).
        for (source in listOf(input) + SiteMirrors.candidates(input)) {
            val fetched = try {
                fetcher.fetch(source, referer = UrlUtils.siteRoot(source))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (verification: NeedsVerificationException) {
                // Only the URL the user gave us should send them to the verify screen.
                if (source == input) throw verification
                continue
            } catch (error: Exception) {
                if (lastError == null) lastError = error
                continue
            }
            anyPageLoaded = true

            var parsed = EpisodeListParser.parse(fetched.html, fetched.finalUrl)
            if (parsed.isEmpty() && fetched.finalUrl != source) {
                // A WebView/redirect can return HTML whose final URL is a challenge or
                // tracking URL. Parse once against the URL the user actually selected so
                // relative episode links still resolve against the anime page.
                parsed = EpisodeListParser.parse(fetched.html, source)
            }
            if (parsed.isNotEmpty()) {
                page = fetched
                episodes = parsed
                break
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
            val networkError = lastError as? NetworkUnavailableException
            if (!anyPageLoaded && networkError != null) throw networkError
            throw FetchFailureException(
                "Tidak ada episode yang terdeteksi di halaman ini. " +
                    "Pastikan URL halaman anime benar.",
            )
        }

        val resolvedPage = page ?: throw FetchFailureException(
            "Tidak ada episode yang terdeteksi di halaman ini. " +
                "Pastikan URL halaman anime benar.",
        )
        val animeSlug = try {
            UrlUtils.lastPathSegment(URI(resolvedPage.finalUrl).path.orEmpty())
        } catch (_: Exception) {
            UrlUtils.lastPathSegment(input)
        }

        Timber.i(
            "Detected %d episode(s) for %s via %s",
            episodes.size,
            animeSlug,
            resolvedPage.finalUrl,
        )
        return DetectionResult(
            sourceUrl = input,
            finalUrl = resolvedPage.finalUrl,
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
                Timber.i("Search '%s' on %s: %d result(s)", term, host, results.size)
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
        freshLinks(key)?.let {
            Timber.i("Using cached links for %s from %s", key, it.sourceUrl ?: key)
            return it
        }
        Timber.i("Reading download links for %s", episodeUrl)

        val ownHost = UrlUtils.hostOf(episodeUrl)
        val ownIsPrimary = SiteMirrors.isPrimaryHost(ownHost)

        var failure: Exception? = null
        var verification: NeedsVerificationException? = null
        var best: DownloadLinks? = null
        var bestSource: String? = null

        suspend fun trySource(source: String) {
            val page = try {
                fetcher.fetchDownloadLinksPage(source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (challenge: NeedsVerificationException) {
                // Only the URL the user gave us should send them to the verify screen.
                if (source == episodeUrl) verification = challenge
                return
            } catch (error: Exception) {
                Timber.d(error, "Episode page unavailable at %s", source)
                failure = failure ?: error
                return
            }
            val servedUrl = page.finalUrl.ifBlank { source }
            if (!sameEpisodePage(key, servedUrl)) {
                Timber.w("Ignore links from another episode: %s (requested %s)", servedUrl, key)
                return
            }
            val links = DownloadLinkParser.parse(page.html, page.finalUrl)
            if (links == null || links.isEmpty) return
            Timber.i(
                "Links from %s: %s",
                servedUrl,
                links.data.entries.joinToString { (format, resolutions) ->
                    "$format=${resolutions.entries.joinToString { "${it.key}:${it.value.size}" }}"
                },
            )
            if (best == null || links.weight() > best!!.weight()) {
                best = links
                bestSource = servedUrl
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
            Timber.i("Selected links for %s from %s", key, served)
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
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (challenge: NeedsVerificationException) {
                    continue
                } catch (error: Exception) {
                    Timber.d(error, "Mirror page unavailable at %s", url)
                    continue
                }
                val servedUrl = page.finalUrl.ifBlank { url }
                if (!sameEpisodePage(episodeUrl, servedUrl)) {
                    Timber.w("Ignore mirror page for another episode: %s", servedUrl)
                    continue
                }
                val links = DownloadLinkParser.parse(page.html, page.finalUrl)
                if (links == null || links.isEmpty) continue
                UrlUtils.hostOf(servedUrl)?.let { coveredHosts += it }
                if (results.any { it.data == links.data }) continue
                Timber.i("Mirror %s served links for %s", servedUrl, episodeUrl)
                // Keep the page that actually served the links so the queue can
                // report which mirror was used instead of a generic message.
                results += links.copy(sourceUrl = servedUrl)
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

        Timber.i("Found %d distinct mirror link set(s) for %s", results.size, episodeUrl)
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
            val match = episodes.firstOrNull { episode ->
                episode.number == ref.number && episodeBelongsToAnime(episode, ref.animeSlug)
            }
            if (match == null) {
                Timber.w(
                    "%s has no episode %d for %s; refusing to use another anime",
                    animePage.finalUrl,
                    ref.number,
                    ref.animeSlug,
                )
                continue
            }
            Timber.i("Mirror episode for %s: %s", ref.animeSlug, match.url)
            urls += match.url
        }
        return urls.toList()
    }

    /**
     * Finds the anime page on a mirror, but only when the search result really is the
     * same series. Taking the first search hit used to attach an episode of a different
     * anime (with another quality/host set) to the user's download.
     */
    private suspend fun animePageBySearch(host: String, animeSlug: String): FetchedPage? {
        val term = Names.fromSlug(animeSlug)
        if (term.isBlank()) return null
        val results = searchOnHost(host, term)
        if (results.isEmpty()) {
            Timber.i("Mirror search %s for %s returned no result", host, animeSlug)
            return null
        }

        val matching = results.filter { result ->
            Names.sameAnime(result.slug, animeSlug) ||
                Names.titleMatchesSlug(result.title, animeSlug)
        }
        results.filterNot { it in matching }.forEach { result ->
            Timber.i(
                "Mirror search %s: '%s' (%s) does not match %s",
                host,
                result.title,
                result.slug,
                animeSlug,
            )
        }
        val best = matching.maxByOrNull { result ->
            when {
                Names.sameAnime(result.slug, animeSlug) -> 2
                else -> 1
            }
        } ?: return null

        val page = try {
            fetcher.fetch(best.url, referer = "https://$host/")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return null
        }
        val finalSlug = UrlUtils.lastPathSegment(page.finalUrl)
        val finalMatches = Names.sameAnime(finalSlug, animeSlug) ||
            Names.sameAnime(best.slug, animeSlug) ||
            Names.titleMatchesSlug(best.title, animeSlug)
        if (!finalMatches) {
            Timber.w(
                "Mirror search %s redirected to another anime: %s",
                host,
                page.finalUrl,
            )
            return null
        }
        Timber.i("Mirror search %s matched %s -> %s", host, animeSlug, page.finalUrl)
        return page
    }

    private fun episodeBelongsToAnime(episode: Episode, animeSlug: String): Boolean {
        val episodeAnime = SiteMirrors.episodeRef(episode.url)?.animeSlug ?: return false
        return Names.sameAnime(episodeAnime, animeSlug)
    }

    /** Guard for pages that redirect to a different anime's episode. */
    private fun sameEpisodePage(expectedUrl: String, candidateUrl: String): Boolean {
        val expected = SiteMirrors.episodeRef(expectedUrl) ?: return true
        val candidate = SiteMirrors.episodeRef(candidateUrl) ?: return true
        return expected.number == candidate.number &&
            Names.sameAnime(expected.animeSlug, candidate.animeSlug)
    }

    private suspend fun searchOnHost(host: String, term: String): List<AnimeSearchResult> {
        val root = "https://$host/"
        val url = "$root?s=" + URLEncoder.encode(term, "UTF-8")
        val page = try {
            fetcher.fetch(url, referer = root)
        } catch (cancellation: CancellationException) {
            throw cancellation
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
