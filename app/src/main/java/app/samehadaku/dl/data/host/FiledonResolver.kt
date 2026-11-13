package app.samehadaku.dl.data.host

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup

object FiledonUrlParser {

    const val BASE_URL = "https://filedon.co"

    private val SHARE_LINK = Regex(
        """^https?://(?:www\.)?filedon\.(?:co|net|io|app)/(?:view|file)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun shareId(url: String): String? = SHARE_LINK.find(url)?.groupValues?.get(1)

    fun pageUrl(id: String): String = "$BASE_URL/view/$id"

    fun downloadUrl(id: String): String = "$BASE_URL/download/$id"
}

/**
 * Reads the Inertia page object Filedon returns for a share page. The same shape is
 * used for the HTML `data-page` attribute and for JSON redirect responses, so both
 * bodies go through [parseBody].
 */
internal object FiledonPage {

    fun parse(html: String): JSONObject? {
        val raw = Jsoup.parse(html).selectFirst("[data-page]")?.attr("data-page")
            ?: return null
        return parseJson(raw)
    }

    fun parseBody(body: String): JSONObject? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            parseJson(trimmed)?.let { return it }
        }
        return parse(trimmed)
    }

    fun csrfToken(json: JSONObject): String? =
        props(json)?.optJSONObject("flash")?.optString("_token")
            ?.takeIf { it.isNotBlank() }

    fun downloadUrl(json: JSONObject): String? =
        props(json)?.optJSONObject("flash")?.optString("download_url")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    fun unavailableReason(json: JSONObject): String? {
        val props = props(json) ?: return null
        val meta = props.optJSONObject("sharing_meta")
        if (meta?.optBoolean("is_expired") == true) {
            return "Link Filedon sudah kedaluwarsa"
        }
        if (meta?.optBoolean("limit_reached") == true) {
            return "Batas unduhan Filedon sudah tercapai"
        }

        val sharing = props.optJSONObject("sharing") ?: return null
        if (!sharing.optBoolean("status", true)) {
            return "Link Filedon sudah tidak aktif"
        }
        if (!sharing.optBoolean("allow_download", true)) {
            return "Unduhan untuk file Filedon ini tidak diizinkan"
        }

        val file = sharing.optJSONObject("files") ?: props.optJSONObject("files")
        if (file != null) {
            val status = file.optString("status")
            if (status.isNotEmpty() && status != "active") {
                return "File sudah dihapus dari Filedon"
            }
            if (!file.isNull("deleted_at")) {
                return "File sudah dihapus dari Filedon"
            }
        }

        val limit = sharing.optInt("download_limit", 0)
        if (limit > 0 && sharing.optInt("download_count", 0) >= limit) {
            return "Batas unduhan Filedon sudah tercapai"
        }
        return null
    }

    private fun props(json: JSONObject): JSONObject? = json.optJSONObject("props")

    private fun parseJson(raw: String): JSONObject? = try {
        JSONObject(raw)
    } catch (_: Exception) {
        null
    }
}

/**
 * Filedon share page -> signed direct URL. The page is a Laravel/Inertia app that
 * only exposes the signed storage link after POSTing `/download/{slug}` with the
 * session cookies and CSRF token of the share page, so the browser flow is replayed:
 * GET page (token + session), POST download, then read `flash.download_url` from the
 * page props that follow the redirect.
 */
@Singleton
class FiledonResolver @Inject constructor(
    private val clients: HttpClientFactory,
) : HostResolver {

    override val host: Host = Host.FILEDON

    /**
     * Cookies must survive redirects too: the POST hands out a fresh session and
     * XSRF cookie before redirecting to the page that contains the download URL.
     */
    private val cookieJar = FiledonCookieJar()
    private val client by lazy {
        clients.plainClient.newBuilder()
            .cookieJar(cookieJar)
            .build()
    }

    /**
     * The queue preflights a link and then resolves it again for the download.
     * Filedon needs a page GET plus a CSRF POST for every resolution, so remember
     * the signed URL briefly instead of replaying the whole browser flow twice.
     */
    private val cacheLock = Any()
    private val resolutions = LinkedHashMap<String, CachedResolution>()

    override suspend fun resolve(url: String): HostResolveResult = withContext(Dispatchers.IO) {
        val shareId = FiledonUrlParser.shareId(url)
            ?: return@withContext permanent("Link Filedon tidak valid")
        cachedResolution(shareId)?.let { return@withContext it }

        val result = resolveShare(shareId)
        if (result is HostResolveResult.Success) {
            cacheResolution(shareId, result)
        }
        result
    }

    private fun resolveShare(shareId: String): HostResolveResult {
        val pageUrl = FiledonUrlParser.pageUrl(shareId)

        val page = perform(pageRequest(pageUrl))
            ?: return retryable(NETWORK_ERROR)
        failureFor(page.code)?.let { return it }
        if (page.code != HTTP_OK) {
            return retryable("Filedon mengembalikan HTTP ${page.code}")
        }

        val props = FiledonPage.parse(page.body)
            ?: return retryable("Halaman Filedon tidak dapat dibaca")
        FiledonPage.unavailableReason(props)?.let { return permanent(it) }

        val xsrf = xsrfToken(pageUrl)
        val csrf = FiledonPage.csrfToken(props)
        if (xsrf == null && csrf == null) {
            return retryable("Sesi Filedon tidak ditemukan")
        }

        val download = perform(downloadRequest(shareId, pageUrl, xsrf, csrf))
            ?: return retryable(NETWORK_ERROR)
        when (download.code) {
            HTTP_FORBIDDEN, HTTP_SESSION_EXPIRED -> return retryable(
                "Sesi unduhan Filedon ditolak; coba lagi nanti",
            )
        }
        failureFor(download.code)?.let { return it }
        if (download.code !in 200..399) {
            return retryable("Filedon mengembalikan HTTP ${download.code}")
        }

        // Check the URL first: a successful download can also push the share to
        // its download limit, which must not turn a valid flash URL into a failure.
        val downloadProps = FiledonPage.parseBody(download.body)
        if (downloadProps != null) {
            FiledonPage.downloadUrl(downloadProps)?.let { direct ->
                return HostResolveResult.Success(direct)
            }
            FiledonPage.unavailableReason(downloadProps)?.let { reason ->
                return permanent(reason)
            }
        }

        // Some responses land on the share page without the flash data; ask once
        // more from the page itself before reporting a retryable failure.
        val retryPage = perform(pageRequest(pageUrl))
        val retryProps = FiledonPage.parseBody(retryPage?.body.orEmpty())
        if (retryProps != null) {
            FiledonPage.downloadUrl(retryProps)?.let { direct ->
                return HostResolveResult.Success(direct)
            }
            FiledonPage.unavailableReason(retryProps)?.let { reason ->
                return permanent(reason)
            }
        }

        return retryable("Filedon belum memberikan link unduhan; coba lagi nanti")
    }

    private fun cachedResolution(shareId: String): HostResolveResult.Success? {
        synchronized(cacheLock) {
            val cached = resolutions[shareId] ?: return null
            if (cached.expiresAt > System.currentTimeMillis()) return cached.result
            resolutions.remove(shareId)
        }
        return null
    }

    private fun cacheResolution(shareId: String, result: HostResolveResult.Success) {
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            resolutions.entries.removeAll { it.value.expiresAt <= now }
            while (resolutions.size >= CACHE_SIZE) {
                val oldest = resolutions.keys.firstOrNull() ?: break
                resolutions.remove(oldest)
            }
            resolutions[shareId] = CachedResolution(
                result = result,
                expiresAt = now + CACHE_TTL_MS,
            )
        }
    }

    private fun pageRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", HTML_ACCEPT)
        .build()

    private fun downloadRequest(
        shareId: String,
        pageUrl: String,
        xsrf: String?,
        csrf: String?,
    ): Request {
        val token = xsrf ?: csrf
        return Request.Builder()
            .url(FiledonUrlParser.downloadUrl(shareId))
            .header("Accept", HTML_ACCEPT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", pageUrl)
            .header("Origin", FiledonUrlParser.BASE_URL)
            .apply {
                if (token != null) {
                    header(if (xsrf != null) "X-XSRF-TOKEN" else "X-CSRF-TOKEN", token)
                }
            }
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun perform(request: Request): FiledonResponse? = try {
        client.newCall(request).execute().use { response ->
            FiledonResponse(
                code = response.code,
                body = try {
                    response.body?.string().orEmpty()
                } catch (_: IOException) {
                    ""
                },
            )
        }
    } catch (_: IOException) {
        null
    }

    private fun xsrfToken(pageUrl: String): String? =
        cookieJar.loadForRequest(pageUrl.toHttpUrl())
            .firstOrNull { it.name == XSRF_COOKIE }
            ?.value
            ?.let { value ->
                try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    null
                }
            }
            ?.takeIf { it.isNotBlank() }

    private fun failureFor(code: Int): HostResolveResult.Failure? = when (code) {
        404, 410 -> permanent("File sudah dihapus dari Filedon")
        451 -> permanent("File Filedon diblokir oleh host")
        429 -> retryable("Terkena limit Filedon, coba lagi nanti")
        else -> null
    }

    private fun permanent(reason: String) =
        HostResolveResult.Failure(reason = reason, permanent = true)

    private fun retryable(reason: String) =
        HostResolveResult.Failure(reason = reason, permanent = false)

    private data class FiledonResponse(
        val code: Int,
        val body: String,
    )

    private data class CachedResolution(
        val result: HostResolveResult.Success,
        val expiresAt: Long,
    )

    /** Minimal browser-like cookie jar for the Filedon session and XSRF cookies. */
    private class FiledonCookieJar : CookieJar {

        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                this.cookies.removeAll { existing ->
                    existing.name == cookie.name &&
                        existing.domain == cookie.domain &&
                        existing.path == cookie.path
                }
                this.cookies += cookie
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SESSION_EXPIRED = 419
        const val XSRF_COOKIE = "XSRF-TOKEN"
        const val NETWORK_ERROR = "Gagal menghubungi Filedon: kesalahan jaringan"
        const val HTML_ACCEPT = "text/html,application/xhtml+xml"
        const val CACHE_SIZE = 32
        const val CACHE_TTL_MS = 5 * 60 * 1000L

        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
