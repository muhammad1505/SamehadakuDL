package app.samehadaku.dl.presentation.cloudflare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.CloudflareSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudflareUiState(
    val url: String,
    val pageLoading: Boolean = true,
    val challengeDetected: Boolean = false,
    val statusMessage: String? = null,
    val verified: Boolean = false,
    val saving: Boolean = false,
    val sessionSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CloudflareViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val cloudflareSession: CloudflareSession,
) : ViewModel() {

    private var lastPage: PageSnapshot? = null
    private var autoSaveStarted = false

    private val _state = MutableStateFlow(
        CloudflareUiState(
            url = sessionStore.pendingVerificationUrl ?: DEFAULT_SITE,
            sessionSaved = cloudflareSession.hasSession(),
        ),
    )
    val state: StateFlow<CloudflareUiState> = _state.asStateFlow()

    fun onPageStarted() {
        _state.update {
            it.copy(
                pageLoading = true,
                challengeDetected = false,
                statusMessage = "Mendeteksi pemeriksaan Cloudflare…",
                error = null,
            )
        }
    }

    fun onPageFinished(url: String, title: String, cookies: String?, userAgent: String) {
        lastPage = PageSnapshot(url, title, cookies.orEmpty(), userAgent)
        val challengeDetected = isChallenge(title, url)
        val hasClearance = cookies?.contains("cf_clearance") == true
        _state.update {
            it.copy(
                pageLoading = false,
                challengeDetected = challengeDetected,
                statusMessage = when {
                    hasClearance -> "Sesi Cloudflare ditemukan. Menyimpan verifikasi…"
                    challengeDetected -> "Pemeriksaan Cloudflare ditemukan. Selesaikan tantangan di halaman ini."
                    else -> "Pemeriksaan Cloudflare tidak terdeteksi di halaman ini."
                },
            )
        }

        if (hasClearance && !challengeDetected && !autoSaveStarted) {
            autoSaveStarted = true
            persistSession(thenVerify = true)
        }
    }

    fun onPageError() {
        _state.update {
            it.copy(
                pageLoading = false,
                statusMessage = "Halaman Cloudflare gagal dimuat.",
                error = "Halaman gagal dimuat. Periksa koneksi lalu coba lagi.",
            )
        }
    }

    fun manualFinish(onDone: () -> Unit) {
        val page = lastPage
        viewModelScope.launch {
            var saved = false
            if (page != null) {
                _state.update { it.copy(saving = true) }
                saved = runCatching { savePage(page) }.getOrDefault(false)
                _state.update {
                    it.copy(
                        saving = false,
                        sessionSaved = it.sessionSaved || saved,
                    )
                }
            }
            if (saved || _state.value.sessionSaved) {
                onDone()
            } else {
                _state.update {
                    it.copy(
                        error = "Verifikasi belum terdeteksi. Selesaikan tantangan Cloudflare " +
                            "atau muat ulang halaman terlebih dahulu.",
                        statusMessage = "Belum ada sesi Cloudflare yang bisa disimpan.",
                    )
                }
            }
        }
    }

    private fun persistSession(thenVerify: Boolean) {
        val page = lastPage ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val saved = savePage(page)
                _state.update {
                    it.copy(
                        saving = false,
                        sessionSaved = it.sessionSaved || saved,
                        verified = thenVerify && saved,
                        statusMessage = if (thenVerify && saved) {
                            "Verifikasi Cloudflare berhasil dan sesi sudah tersimpan."
                        } else {
                            it.statusMessage
                        },
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        error = "Gagal menyimpan sesi Cloudflare: ${error.message}",
                    )
                }
            }
        }
    }

    private suspend fun savePage(page: PageSnapshot): Boolean {
        val host = UrlUtils.hostOf(page.url)
            ?: UrlUtils.hostOf(sessionStore.pendingVerificationUrl.orEmpty())
            ?: DEFAULT_HOST
        if (page.cookies.isBlank() || page.userAgent.isBlank()) return false
        cloudflareSession.save(host, page.userAgent, page.cookies)
        sessionStore.pendingVerificationUrl = null
        return true
    }

    private fun isChallenge(title: String, url: String): Boolean {
        val value = "$title $url".lowercase()
        return CHALLENGE_MARKERS.any { marker -> value.contains(marker) }
    }

    private data class PageSnapshot(
        val url: String,
        val title: String,
        val cookies: String,
        val userAgent: String,
    )

    private companion object {
        const val DEFAULT_SITE = "https://samehadaku.how/"
        const val DEFAULT_HOST = "samehadaku.how"
        val CHALLENGE_MARKERS = listOf(
            "just a moment",
            "attention required",
            "cloudflare",
            "challenge-platform",
            "/cdn-cgi/challenge",
        )
    }
}
