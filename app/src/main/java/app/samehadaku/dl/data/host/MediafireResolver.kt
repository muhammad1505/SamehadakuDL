package app.samehadaku.dl.data.host

import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import app.samehadaku.dl.data.network.HttpClientFactory
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Mediafire page -> direct download URL. Network access is delegated to a plain OkHttp
 * client because Mediafire does not require the Cloudflare session.
 */
@Singleton
class MediafireResolver @Inject constructor(
    private val clients: HttpClientFactory,
) : HostResolver {

    override val host: Host = Host.MEDIAFIRE

    override suspend fun resolve(url: String): HostResolveResult {
        var reason = "tombol unduh tidak ditemukan (kemungkinan file sudah dihapus)"
        var permanent = false

        for (candidate in MediafireUrlParser.buildCandidates(url)) {
            val request = Request.Builder()
                .url(candidate)
                .header("User-Agent", BROWSER_UA)
                .build()

            val response = try {
                clients.plainClient.newCall(request).execute()
            } catch (error: Exception) {
                reason = "gagal menghubungi Mediafire: ${error.message ?: "kesalahan jaringan"}"
                continue
            }

            try {
                if (response.code != 200) {
                    reason = mediafireHttpReason(response.code)
                    permanent = response.code in PERMANENT_CODES
                    continue
                }
                val body = response.body?.string().orEmpty()
                val document = Jsoup.parse(body, candidate)
                parseDirectLink(document, body)?.let { direct ->
                    return HostResolveResult.Success(direct)
                }
                if (DEAD_MARKERS.any { marker -> marker in body.lowercase() }) {
                    reason = "file sudah dihapus / link tidak valid di Mediafire"
                    permanent = true
                }
            } finally {
                response.close()
            }
        }

        return HostResolveResult.Failure(reason, permanent)
    }

    private fun parseDirectLink(document: Document, body: String): String? {
        val button = document.selectFirst("a#downloadButton")
        if (button != null) {
            val scrambled = button.attr("data-scrambled-url")
            if (scrambled.isNotEmpty()) {
                decodeBase64(scrambled)?.let { return it }
            }
            val href = button.attr("href")
            if (href.startsWith("http")) return href
        }
        return DIRECT_LINK_REGEX.find(body)?.value
    }

    private fun decodeBase64(value: String): String? {
        val decoders = listOf(
            java.util.Base64.getDecoder(),
            java.util.Base64.getUrlDecoder(),
        )
        for (decoder in decoders) {
            try {
                return String(decoder.decode(value), Charsets.UTF_8)
            } catch (_: Exception) {
                // Try the next decoder.
            }
        }
        return null
    }

    private fun mediafireHttpReason(code: Int): String = when (code) {
        451 -> "diblokir/dihapus oleh host (takedown), bukan masalah aplikasi"
        404, 410 -> "file sudah dihapus dari host"
        429 -> "terkena limit host, coba lagi nanti"
        else -> "HTTP $code"
    }

    companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val PERMANENT_CODES = setOf(404, 410, 451)

        private val DIRECT_LINK_REGEX =
            Regex("https?://download\\d*\\.mediafire\\.com/[^\\s\"'<>]+")

        private val DEAD_MARKERS = listOf(
            "invalid or deleted file",
            "has been removed",
            "file removed",
            "key you provided",
        )
    }
}
