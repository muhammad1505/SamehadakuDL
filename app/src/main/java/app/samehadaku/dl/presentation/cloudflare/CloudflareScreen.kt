package app.samehadaku.dl.presentation.cloudflare

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import app.samehadaku.dl.core.UrlUtils
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareScreen(
    onVerified: () -> Unit,
    onClose: () -> Unit,
    viewModel: CloudflareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSuccess by remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentViewModel by rememberUpdatedState(viewModel)
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(state.verified) {
        if (state.verified) showSuccess = true
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
    }

    fun finish() {
        currentViewModel.manualFinish { currentOnClose() }
    }

    val canFinish = state.challengeDetected || state.sessionSaved || state.verified

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {
                showSuccess = false
                onVerified()
            },
            containerColor = app.samehadaku.dl.presentation.theme.SlateSurface,
            titleContentColor = app.samehadaku.dl.presentation.theme.TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Verifikasi berhasil") },
            text = {
                Text("Sesi Cloudflare sudah tersimpan. Kamu bisa melanjutkan proses sebelumnya.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showSuccess = false
                        onVerified()
                    },
                ) {
                    Text("Lanjutkan", color = BrandPrimary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Verifikasi",
                subtitle = if (state.sessionSaved) {
                    "Sesi Cloudflare tersimpan"
                } else {
                    "Selesaikan pemeriksaan Cloudflare"
                },
                onBack = { if (canFinish) finish() else currentOnClose() },
                actions = {
                    GlassButton(
                        text = if (canFinish) "Selesai" else "Batal",
                        onClick = { if (canFinish) finish() else currentOnClose() },
                        icon = if (canFinish) Icons.Rounded.Check else Icons.Rounded.Close,
                        height = 40.dp,
                        tint = if (canFinish) BrandPrimary else Danger,
                    )
                },
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CloudflareWebView(
                url = state.url,
                viewModel = currentViewModel,
            )

            if (state.pageLoading || state.saving) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = BrandPrimary,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = if (state.saving) "Menyimpan sesi…" else "Memuat halaman…",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            if (!state.pageLoading && state.statusMessage != null) {
                InfoBanner(
                    title = if (state.challengeDetected) {
                        "Cloudflare ditemukan"
                    } else if (state.sessionSaved) {
                        "Sesi Cloudflare aktif"
                    } else {
                        "Cloudflare tidak terdeteksi"
                    },
                    message = state.statusMessage.orEmpty(),
                    icon = Icons.Rounded.Cloud,
                    accent = if (state.challengeDetected || state.sessionSaved) {
                        BrandPrimary
                    } else {
                        BrandGold
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                )
            }

            if (state.error != null) {
                InfoBanner(
                    title = "Verifikasi belum selesai",
                    message = state.error.orEmpty(),
                    icon = Icons.Rounded.Cloud,
                    accent = Danger,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CloudflareWebView(
    url: String,
    viewModel: CloudflareViewModel,
) {
    val currentViewModel by rememberUpdatedState(viewModel)
    val context = androidx.compose.ui.platform.LocalContext.current
    val webView = remember(url) {
        WebView(context).apply webView@{
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    currentViewModel.onPageStarted()
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    if (view == null || loadedUrl == null) return
                    val cookies = mergeCookies(
                        CookieManager.getInstance().getCookie(loadedUrl),
                        CookieManager.getInstance().getCookie(UrlUtils.siteRoot(loadedUrl)),
                    )
                    currentViewModel.onPageFinished(
                        url = loadedUrl,
                        title = view.title.orEmpty(),
                        cookies = cookies,
                        userAgent = view.settings.userAgentString,
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) currentViewModel.onPageError()
                }
            }
            loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * CookieManager scopes cookies per URL. Combining the episode page and the site root
 * guarantees cf_clearance is not missed when the cleared page lives on another path.
 */
private fun mergeCookies(vararg headers: String?): String {
    val cookies = linkedMapOf<String, String>()
    headers.filterNotNull().forEach { header ->
        header.split(';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val name = trimmed.substringBefore('=', missingDelimiterValue = "")
            if (name.isNotBlank()) cookies[name] = trimmed
        }
    }
    return cookies.values.joinToString("; ")
}
