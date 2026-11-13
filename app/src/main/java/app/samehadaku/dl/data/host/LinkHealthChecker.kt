package app.samehadaku.dl.data.host

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import app.samehadaku.dl.domain.model.TargetLink
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Result of checking one host link without downloading the file. */
sealed interface LinkHealth {
    data object Alive : LinkHealth
    data class Dead(val reason: String) : LinkHealth
    /** The check could not decide (network error, rate limit, ...); the download must try. */
    data object Unknown : LinkHealth
}

/**
 * Probes a host link with a one byte range request so a dead file is discovered
 * before bandwidth is spent. Only definitive answers are reported as [LinkHealth.Dead];
 * anything else stays [LinkHealth.Unknown] so a flaky network never skips a good file.
 */
@Singleton
class LinkHealthChecker @Inject constructor(
    private val resolvers: HostResolverRegistry,
    private val clients: HttpClientFactory,
) {

    suspend fun check(target: TargetLink): LinkHealth = withContext(Dispatchers.IO) {
        val resolved = when (val result = resolvers.resolve(target.host, target.url)) {
            is HostResolveResult.Success -> result
            is HostResolveResult.Failure ->
                return@withContext if (result.permanent) {
                    LinkHealth.Dead("${target.host.label}: ${result.reason}")
                } else {
                    LinkHealth.Unknown
                }
        }
        probe(target.host, resolved.directUrl, resolved.headers)
    }

    private fun probe(
        host: Host,
        url: String,
        headers: Map<String, String>,
    ): LinkHealth {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .apply {
                for ((name, value) in headers) header(name, value)
            }
            .build()
        return try {
            clients.probeClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 || response.code == 410 ->
                        LinkHealth.Dead("${host.label}: file sudah dihapus")

                    response.code == 451 ->
                        LinkHealth.Dead("${host.label}: diblokir/dihapus oleh host")

                    response.isSuccessful -> {
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        if ("text/html" in contentType) {
                            // Gofile/Acefile/Filedon can answer with a landing or login page
                            // when their session headers are stale; let the download try
                            // instead of skipping the episode as dead.
                            if (host == Host.GOFILE || host == Host.ACEFILE || host == Host.FILEDON) {
                                LinkHealth.Unknown
                            } else {
                                LinkHealth.Dead("${host.label}: server mengirim halaman, bukan file")
                            }
                        } else {
                            LinkHealth.Alive
                        }
                    }

                    else -> LinkHealth.Unknown
                }
            }
        } catch (_: IOException) {
            LinkHealth.Unknown
        }
    }
}
