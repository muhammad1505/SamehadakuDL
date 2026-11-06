package app.samehadaku.dl.data.host

import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import java.net.URLDecoder

interface HostResolver {
    val host: Host
    suspend fun resolve(url: String): HostResolveResult
}

/**
 * Pure parsing helper for pixeldrain page URLs; no network access is required.
 */
object PixeldrainUrlParser {
    fun toDirectUrl(url: String): String? {
        if (!url.contains("pixeldrain.com/u/")) return null
        val id = url.substringAfter("/u/").substringBefore('?').trim('/')
        if (id.isEmpty()) return null
        return "https://pixeldrain.com/api/file/$id?download"
    }
}

class PixeldrainResolver @javax.inject.Inject constructor() : HostResolver {
    override val host: Host = Host.PIXELDRAIN

    override suspend fun resolve(url: String): HostResolveResult {
        val direct = PixeldrainUrlParser.toDirectUrl(url)
        return if (direct != null) {
            HostResolveResult.Success(direct)
        } else {
            HostResolveResult.Failure("Link Pixeldrain tidak valid")
        }
    }
}

object MediafireUrlParser {

    fun buildCandidates(url: String): List<String> {
        val candidates = mutableListOf(url)
        if ("%25" in url) {
            val decoded = try {
                URLDecoder.decode(url.replace("+", "%2B"), "UTF-8")
            } catch (_: Exception) {
                null
            }
            if (decoded != null && decoded != url) candidates.add(decoded)
        }
        return candidates
    }
}
