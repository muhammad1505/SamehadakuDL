package app.samehadaku.dl.data.network

import app.samehadaku.dl.data.parser.DownloadLinkParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber

data class FetchedPage(
    val html: String,
    val finalUrl: String,
    val fromWebView: Boolean = false,
)

@Singleton
class HtmlFetcher @Inject constructor(
    private val clients: HttpClientFactory,
    private val webViewFetcher: WebViewHtmlFetcher,
    private val cloudflareSession: CloudflareSession,
) {

    suspend fun fetch(url: String, referer: String? = null): FetchedPage =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null

            for (attempt in 1..MAX_ATTEMPTS) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .apply {
                            if (!referer.isNullOrBlank()) header("Referer", referer)
                        }
                        .build()

                    clients.siteClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        val finalUrl = response.request.url.toString()
                        val challenged = HttpClientFactory.isChallengeResponse(body)

                        if (response.code in 200..299 && !challenged) {
                            return@withContext FetchedPage(body, finalUrl)
                        }

                        if (challenged || response.code == 403 || response.code == 503) {
                            // Do not retry a Cloudflare challenge with OkHttp.
                            val viaWebView = fetchViaWebViewWhenPossible(url)
                            if (viaWebView != null) {
                                return@withContext FetchedPage(
                                    html = viaWebView,
                                    finalUrl = finalUrl,
                                    fromWebView = true,
                                )
                            }
                            throw NeedsVerificationException(url)
                        }

                        lastError = FetchFailureException("Server mengembalikan HTTP ${response.code}")
                    }
                } catch (error: NeedsVerificationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                    Timber.w(error, "Fetch attempt %d failed for %s", attempt, url)
                }

                if (attempt < MAX_ATTEMPTS) {
                    delay(2_000L * attempt)
                }
            }

            throw FetchFailureException(
                message = "Gagal mengambil halaman: ${lastError?.message ?: "jaringan tidak tersedia"}",
                cause = lastError,
            )
        }

    suspend fun fetchDownloadLinksPage(episodeUrl: String): FetchedPage {
        val page = fetch(episodeUrl, referer = app.samehadaku.dl.core.UrlUtils.siteRoot(episodeUrl))
        val refreshTarget = DownloadLinkParser.findMetaRefreshUrl(page.html, page.finalUrl)
            ?: return page
        return fetch(refreshTarget, referer = app.samehadaku.dl.core.UrlUtils.siteRoot(refreshTarget))
    }

    private suspend fun fetchViaWebViewWhenPossible(url: String): String? {
        if (!cloudflareSession.hasSession()) return null
        val html = try {
            webViewFetcher.fetch(url)
        } catch (_: Exception) {
            null
        } ?: return null
        if (html.isBlank() || HttpClientFactory.isChallengeResponse(html)) return null
        return html
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
