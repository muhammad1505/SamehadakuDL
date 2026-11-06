package app.samehadaku.dl.data.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

/**
 * Second fetch strategy: load the page in an off-screen WebView and read outerHTML.
 * Used when OkHttp gets a Cloudflare challenge even though a valid cf_clearance exists
 * (typically because of a TLS fingerprint mismatch).
 */
@Singleton
class WebViewHtmlFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun fetch(url: String): String? = withContext(Dispatchers.Main) {
        val webView = WebView(context)
        try {
            withTimeoutOrNull(WEBVIEW_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val handler = Handler(Looper.getMainLooper())
                    var resumed = false

                    fun resumeOnce(value: String?) {
                        if (resumed) return
                        resumed = true
                        if (continuation.isActive) continuation.resume(value)
                    }

                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = false
                        mediaPlaybackRequiresUserGesture = true
                    }
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            handler.postDelayed({
                                view?.evaluateJavascript(OUTER_HTML_SCRIPT) { raw ->
                                    resumeOnce(decodeJavascriptString(raw))
                                }
                            }, 750L)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) resumeOnce(null)
                        }
                    }
                    webView.loadUrl(url)
                    continuation.invokeOnCancellation {
                        handler.removeCallbacksAndMessages(null)
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
            }
        } finally {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {
                // WebView destroyed twice (cancellation path) - nothing to do.
            }
        }
    }

    private fun decodeJavascriptString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            when (val value = JSONTokener(raw).nextValue()) {
                is String -> value
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val WEBVIEW_TIMEOUT_MS = 45_000L
        const val OUTER_HTML_SCRIPT =
            "(function(){return document.documentElement.outerHTML;})()"
    }
}
