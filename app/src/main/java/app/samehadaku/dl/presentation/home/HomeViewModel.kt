package app.samehadaku.dl.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.notifications.NotificationRepository
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.usecase.DetectEpisodesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val recentUrls: List<String> = emptyList(),
    val needsVerification: Boolean = false,
    val unreadNotifications: Int = 0,
)

sealed interface HomeEvent {
    data object OpenEpisodes : HomeEvent
    data object OpenQuality : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val detectEpisodes: DetectEpisodesUseCase,
    private val settings: SettingsRepository,
    private val sessionStore: SessionStore,
    private val cloudflareSession: CloudflareSession,
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settings.recentUrls.collect { urls ->
                _state.update { it.copy(recentUrls = urls) }
            }
        }
        viewModelScope.launch {
            notifications.unreadCount.collect { count ->
                _state.update { it.copy(unreadNotifications = count) }
            }
        }
    }

    fun onUrlChange(value: String) {
        _state.update {
            it.copy(url = value, needsVerification = false, error = null)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun detect() {
        val raw = _state.value.url.trim()
        if (raw.isBlank()) {
            _state.update { it.copy(error = "Tempel URL Samehadaku terlebih dahulu.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { cloudflareSession.restore() }
            try {
                val result = detectEpisodes(raw)
                if (result.episodes.isEmpty()) {
                    throw IllegalStateException("Tidak ada episode yang terdeteksi.")
                }
                sessionStore.saveDetection(result)
                settings.addRecentUrl(result.sourceUrl)
                // Detection succeeded and the app moves on to episode/quality picking,
                // so the link field starts empty again when the user comes back.
                _state.update { it.copy(loading = false, needsVerification = false, url = "") }
                _events.send(
                    if (result.isListPage) HomeEvent.OpenEpisodes else HomeEvent.OpenQuality,
                )
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl =
                    verification.pageUrl.ifBlank { UrlUtils.normalizeInput(raw) }
                _state.update {
                    it.copy(
                        loading = false,
                        needsVerification = true,
                        error = null,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Gagal mendeteksi episode.",
                    )
                }
            }
        }
    }

    fun prepareVerification() {
        val raw = _state.value.url.trim()
        if (raw.isNotBlank()) {
            sessionStore.pendingVerificationUrl = UrlUtils.normalizeInput(raw)
        }
    }

    fun onVerifiedReturned() {
        _state.update { it.copy(needsVerification = false) }
        detect()
    }

    fun removeRecentUrl(url: String) {
        viewModelScope.launch {
            runCatching { settings.removeRecentUrl(url) }
        }
    }

    fun clearRecentUrls() {
        viewModelScope.launch {
            runCatching { settings.clearRecentUrls() }
        }
    }
}
