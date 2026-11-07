package app.samehadaku.dl.data.host

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object AcefileUrlParser {

    private val FILE_ID = Regex("""acefile\.co/f/(\d+)""", RegexOption.IGNORE_CASE)

    fun fileId(url: String): String? = FILE_ID.find(url)?.groupValues?.get(1)

    fun pageUrl(id: String): String = "https://acefile.co/f/$id"

    fun mirrorUrl(id: String): String =
        "https://acefile.co/service/mirror_download/$id"
}

/**
 * Acefile page -> free mirror endpoint. Acefile only exposes a direct link when
 * a free mirror is currently available; otherwise the download is login-only.
 * An empty mirror list is not a permanent failure because it can change later.
 */
@Singleton
class AcefileResolver @Inject constructor(
    private val clients: HttpClientFactory,
) : HostResolver {

    override val host: Host = Host.ACEFILE

    override suspend fun resolve(url: String): HostResolveResult = withContext(Dispatchers.IO) {
        val fileId = AcefileUrlParser.fileId(url)
            ?: return@withContext HostResolveResult.Failure(
                "Link Acefile tidak valid",
                permanent = true,
            )
        val pageUrl = AcefileUrlParser.pageUrl(fileId)

        val pageRequest = Request.Builder()
            .url(pageUrl)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        var cookies = ""
        val pageResponse = try {
            clients.plainClient.newCall(pageRequest).execute()
        } catch (error: Exception) {
            return@withContext HostResolveResult.Failure(
                "Gagal menghubungi Acefile: ${error.message ?: "kesalahan jaringan"}",
                permanent = false,
            )
        }

        pageResponse.use { response ->
            cookies = response.headers("Set-Cookie")
                .map { it.substringBefore(';').trim() }
                .filter { it.isNotEmpty() }
                .joinToString("; ")

            if (response.code == 404 || response.code == 410 || response.code == 451) {
                return@withContext HostResolveResult.Failure(
                    "File sudah dihapus dari Acefile",
                    permanent = true,
                )
            }
            if (!response.isSuccessful) {
                return@withContext HostResolveResult.Failure(
                    "Acefile mengembalikan HTTP ${response.code}",
                    permanent = false,
                )
            }

            val body = try {
                response.body?.string().orEmpty()
            } catch (error: Exception) {
                return@withContext HostResolveResult.Failure(
                    "Gagal membaca halaman Acefile: ${error.message ?: "kesalahan jaringan"}",
                    permanent = false,
                )
            }
            val lower = body.lowercase()
            if ("no-login-dl" !in lower && DEAD_MARKERS.any { it in lower }) {
                return@withContext HostResolveResult.Failure(
                    "File sudah dihapus / link tidak valid di Acefile",
                    permanent = true,
                )
            }
        }

        val mirrorRequest = Request.Builder()
            .url(AcefileUrlParser.mirrorUrl(fileId))
            .header("Accept", "application/json")
            .header("Referer", pageUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .apply {
                if (cookies.isNotEmpty()) header("Cookie", cookies)
            }
            .build()

        val mirrorResponse = try {
            clients.plainClient.newCall(mirrorRequest).execute()
        } catch (error: Exception) {
            return@withContext HostResolveResult.Failure(
                "Gagal menghubungi mirror Acefile: ${error.message ?: "kesalahan jaringan"}",
                permanent = false,
            )
        }

        mirrorResponse.use { response ->
            if (response.code == 404 || response.code == 410 || response.code == 451) {
                return@withContext HostResolveResult.Failure(
                    "File sudah dihapus dari Acefile",
                    permanent = true,
                )
            }
            if (response.code == 429) {
                return@withContext HostResolveResult.Failure(
                    "Terkena limit Acefile, coba lagi nanti",
                    permanent = false,
                )
            }
            if (!response.isSuccessful) {
                return@withContext HostResolveResult.Failure(
                    "Acefile mengembalikan HTTP ${response.code}",
                    permanent = false,
                )
            }

            val body = try {
                response.body?.string().orEmpty()
            } catch (error: Exception) {
                return@withContext HostResolveResult.Failure(
                    "Gagal membaca mirror Acefile: ${error.message ?: "kesalahan jaringan"}",
                    permanent = false,
                )
            }
            val direct = try {
                parseDirectLink(body)
            } catch (_: Exception) {
                null
            }
            if (direct != null) {
                return@withContext HostResolveResult.Success(
                    directUrl = direct,
                    headers = if (cookies.isEmpty()) emptyMap() else mapOf("Cookie" to cookies),
                )
            }
            return@withContext HostResolveResult.Failure(
                "Acefile hanya menyediakan unduhan lewat login (mirror gratis belum tersedia)",
                permanent = false,
            )
        }
    }

    private fun parseDirectLink(body: String): String? {
        val text = body.trim()
        if (text.isEmpty()) return null

        val direct = if (text.startsWith("[")) {
            val array = JSONArray(text)
            if (array.length() == 0) null else array.optJSONObject(0)?.optString("direct")
        } else {
            val array = JSONObject(text).optJSONArray("AceFile")
            if (array == null || array.length() == 0) {
                null
            } else {
                array.optJSONObject(0)?.optString("direct")
            }
        }

        return direct?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private companion object {
        val DEAD_MARKERS = listOf(
            "file has been removed",
            "file has been deleted",
            "file not found",
            "file does not exist",
            "this file was deleted",
        )
    }
}
