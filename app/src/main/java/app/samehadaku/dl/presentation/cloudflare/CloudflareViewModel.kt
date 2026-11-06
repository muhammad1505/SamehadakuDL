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
        _state.update { it.copy(pageLoading = true, error = null) }
    }

    fun onPageFinished(url: String, title: String, cookies: String?, userAgent: String) {
        lastPage = PageSnapshot(url, title, cookies.orEmpty(), userAgent)
        _state.update { it.copy(pageLoading = false) }

        val clearedChallenge = !title.contains("just a moment", ignoreCase = true)
        val hasClearance = cookies?.contains("cf_clearance") == true
        if (hasClearance && !autoSaveStarted) {
            autoSaveStarted = true
            persistSession(thenVerify = clearedChallenge)
        }
    }

    fun onPageError() {
        _state.update {
            it.copy(
                pageLoading = false,
                error = "Halaman gagal dimuat. Periksa koneksi lalu coba lagi.",
            )
        }
    }

    fun manualFinish(onDone: () -> Unit) {
        val page = lastPage
        viewModelScope.launch {
            if (page != null) {
                _state.update { it.copy(saving = true) }
                val saved = runCatching { savePage(page) }.getOrDefault(false)
                _state.update {
                    it.copy(
                        saving = false,
                        sessionSaved = it.sessionSaved || saved,
                    )
                }
            }
            onDone()
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

    private data class PageSnapshot(
        val url: String,
        val title: String,
        val cookies: String,
        val userAgent: String,
    )

    private companion object {
        const val DEFAULT_SITE = "https://samehadaku.how/"
        const val DEFAULT_HOST = "samehadaku.how"
    }
}
