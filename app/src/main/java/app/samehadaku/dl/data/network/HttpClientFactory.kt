package app.samehadaku.dl.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class HttpClientFactory @Inject constructor(
    cloudflareInterceptor: CloudflareInterceptor,
) {

    private val browserUserAgentInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (request.header("User-Agent") == null) {
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }

    /** Site requests: Cloudflare cookies + the exact WebView UA when available. */
    val siteClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(cloudflareInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Mediafire pages: no Cloudflare cookies, generic browser UA. */
    val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(browserUserAgentInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Final file downloads: longer read timeout, generic browser UA. */
    val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(browserUserAgentInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Link health probes: one byte requests with short timeouts. */
    val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(browserUserAgentInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun isChallengeResponse(body: String): Boolean {
            val head = body.take(4000).lowercase()
            return CHALLENGE_MARKERS.any { marker -> marker in head }
        }

        private val CHALLENGE_MARKERS = listOf(
            "just a moment",
            "cf-browser-verification",
            "cf-challenge",
            "attention required",
        )
    }
}

class NeedsVerificationException(
    val pageUrl: String,
    message: String = "Situs meminta verifikasi Cloudflare",
) : Exception(message)

class FetchFailureException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class NetworkUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
