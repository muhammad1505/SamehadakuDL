package app.samehadaku.dl.data.host

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup

object FiledonUrlParser {

    private val SHARE_ID = Regex(
        """filedon\.(?:co|net|io|app)/(?:view|file)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun shareId(url: String): String? = SHARE_ID.find(url)?.groupValues?.get(1)

    fun pageUrl(id: String): String = "https://filedon.co/view/$id"

    fun downloadUrl(id: String): String = "https://filedon.co/download/$id"
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

    override suspend fun resolve(url: String): HostResolveResult = withContext(Dispatchers.IO) {
        val shareId = FiledonUrlParser.shareId(url)
            ?: return@withContext permanent("Link Filedon tidak valid")
        val pageUrl = FiledonUrlParser.pageUrl(shareId)
        val cookies = CookieStore()

        val pageRequest = Request.Builder()
            .url(pageUrl)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val page = perform(pageRequest)
            ?: return@withContext retryable("Gagal menghubungi Filedon: kesalahan jaringan")

        cookies.absorb(page.setCookies)
        failureFor(page.code)?.let { return@withContext it }
        if (page.code != 200) {
            return@withContext retryable("Filedon mengembalikan HTTP ${page.code}")
        }

        val props = pageProps(page.body)
            ?: return@withContext retryable("Halaman Filedon tidak dapat dibaca")
        deadReason(props)?.let { return@withContext permanent(it) }

        val token = flashToken(props)
        val xsrf = cookies.xsrfToken()
        if (token.isNullOrBlank() && xsrf.isNullOrBlank()) {
            return@withContext retryable("Sesi Filedon tidak ditemukan")
        }

        val downloadRequest = Request.Builder()
            .url(FiledonUrlParser.downloadUrl(shareId))
            .header("Accept", "text/html,application/xhtml+xml")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", pageUrl)
            .header("Origin", "https://filedon.co")
            .apply {
                if (cookies.isNotEmpty()) header("Cookie", cookies.header())
                if (xsrf != null) header("X-XSRF-TOKEN", xsrf)
                else header("X-CSRF-TOKEN", token.orEmpty())
            }
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val afterDownload = perform(downloadRequest)
            ?: return@withContext retryable("Gagal menghubungi Filedon: kesalahan jaringan")
        cookies.absorb(afterDownload.setCookies)

        when (afterDownload.code) {
            403, 419 -> return@withContext retryable(
                "Sesi unduhan Filedon ditolak; coba lagi nanti",
            )
        }
        failureFor(afterDownload.code)?.let { return@withContext it }
        if (afterDownload.code !in 200..399) {
            return@withContext retryable("Filedon mengembalikan HTTP ${afterDownload.code}")
        }

        downloadUrlFrom(afterDownload.body)?.let { direct ->
            return@withContext HostResolveResult.Success(direct)
        }

        // Some responses land on the share page without the flash data; ask once
        // more from the page itself before reporting a retryable failure.
        val retryPage = perform(
            Request.Builder()
                .url(pageUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies.header()) }
                .build(),
        )
        retryPage?.let { cookies.absorb(it.setCookies) }
        downloadUrlFrom(retryPage?.body.orEmpty())?.let { direct ->
            return@withContext HostResolveResult.Success(direct)
        }

        retryable("Filedon belum memberikan link unduhan; coba lagi nanti")
    }

    private fun perform(request: Request): FiledonResponse? = try {
        clients.plainClient.newCall(request).execute().use { response ->
            FiledonResponse(
                code = response.code,
                body = try {
                    response.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                },
                setCookies = response.headers("Set-Cookie"),
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun pageProps(html: String): JSONObject? {
        val raw = Jsoup.parse(html).selectFirst("[data-page]")?.attr("data-page")
            ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun propsOf(json: JSONObject): JSONObject? = json.optJSONObject("props")

    private fun flashToken(json: JSONObject): String? =
        propsOf(json)?.optJSONObject("flash")?.optString("_token")
            ?.takeIf { it.isNotBlank() }

    private fun downloadUrlFrom(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val json = try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                null
            }
            json?.let { downloadUrlOf(it) }?.let { return it }
        }
        val json = pageProps(body) ?: return null
        return downloadUrlOf(json)
    }

    private fun downloadUrlOf(json: JSONObject): String? =
        propsOf(json)?.optJSONObject("flash")?.optString("download_url")
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun deadReason(json: JSONObject): String? {
        val sharing = propsOf(json)?.optJSONObject("sharing") ?: return null
        if (sharing.has("status") && !sharing.optBoolean("status", true)) {
            return "Link Filedon sudah tidak aktif"
        }
        if (sharing.has("allow_download") && !sharing.optBoolean("allow_download", true)) {
            return "Unduhan untuk file Filedon ini tidak diizinkan"
        }
        val file = sharing.optJSONObject("files")
        if (file != null) {
            val status = file.optString("status")
            if (status.isNotEmpty() && status != "null" && status != "active") {
                return "File sudah dihapus dari Filedon"
            }
            if (!file.isNull("deleted_at")) {
                return "File sudah dihapus dari Filedon"
            }
        }
        val limit = if (sharing.isNull("download_limit")) {
            0
        } else {
            sharing.optInt("download_limit", 0)
        }
        if (limit > 0 && sharing.optInt("download_count", 0) >= limit) {
            return "Batas unduhan Filedon sudah tercapai"
        }
        return null
    }

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
        val setCookies: List<String>,
    )

    /** Minimal cookie store: the share page hands out the session and XSRF cookies. */
    private class CookieStore {

        private val cookies = linkedMapOf<String, String>()

        fun absorb(setCookies: List<String>) {
            for (raw in setCookies) {
                val pair = raw.substringBefore(';').trim()
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
            }
        }

        fun isNotEmpty(): Boolean = cookies.isNotEmpty()

        fun header(): String =
            cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

        fun xsrfToken(): String? = cookies["XSRF-TOKEN"]?.let { value ->
            try {
                URLDecoder.decode(value, "UTF-8")
            } catch (_: Exception) {
                null
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
