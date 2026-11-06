package app.samehadaku.dl.data.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class CloudflareInterceptor @Inject constructor(
    private val session: CloudflareSession,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val snapshot = session.snapshot()
        if (snapshot == null || !session.appliesTo(request.url.host)) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()
            .header("User-Agent", snapshot.userAgent)
            .header("Cookie", snapshot.cookies)
        if (request.header("Referer") == null) {
            builder.header("Referer", "https://${snapshot.host}/")
        }
        return chain.proceed(builder.build())
    }
}
