package app.samehadaku.dl.presentation.search

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.repository.AnimeCoverRepository
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.usecase.AddSubscriptionUseCase
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

data class SearchDetailUiState(
    val result: AnimeSearchResult? = null,
    val cover: Bitmap? = null,
    val inspecting: Boolean = false,
    val episodeCount: Int? = null,
    val busy: Boolean = false,
    val busyAction: String? = null,
    val needsVerification: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

sealed interface SearchDetailEvent {
    data object OpenEpisodes : SearchDetailEvent
    data object OpenQuality : SearchDetailEvent
}

@HiltViewModel
class SearchDetailViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val coverRepository: AnimeCoverRepository,
    private val detectEpisodes: DetectEpisodesUseCase,
    private val addSubscription: AddSubscriptionUseCase,
    private val settings: SettingsRepository,
    private val cloudflareSession: CloudflareSession,
) : ViewModel() {

    private val result = sessionStore.selectedSearchResult
    private val _state = MutableStateFlow(SearchDetailUiState(result = result))
    val state: StateFlow<SearchDetailUiState> = _state.asStateFlow()

    private val _events = Channel<SearchDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        result?.let { selected ->
            viewModelScope.launch {
                _state.update { it.copy(cover = coverRepository.load(selected.slug)) }
            }
            load()
        }
    }

    fun load() {
        val selected = result ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(inspecting = true, error = null, needsVerification = false)
            }
            runCatching { cloudflareSession.restore() }
            try {
                val detection = detectEpisodes(selected.url)
                sessionStore.saveDetection(detection)
                _state.update {
                    it.copy(
                        inspecting = false,
                        episodeCount = detection.episodes.size,
                        error = null,
                    )
                }
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl.ifBlank { selected.url }
                _state.update {
                    it.copy(inspecting = false, needsVerification = true)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        inspecting = false,
                        error = error.message ?: "Ringkasan episode belum bisa dimuat.",
                    )
                }
            }
        }
    }

    fun download() {
        val selected = result ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyAction = "download", error = null) }
            try {
                val detection = sessionStore.detection?.takeIf {
                    it.sourceUrl.equals(selected.url, ignoreCase = true) ||
                        it.finalUrl.equals(selected.url, ignoreCase = true)
                } ?: detectEpisodes(selected.url)
                sessionStore.saveDetection(detection)
                settings.addRecentUrl(detection.sourceUrl)
                _state.update { it.copy(busy = false, busyAction = null) }
                _events.send(
                    if (detection.isListPage) SearchDetailEvent.OpenEpisodes
                    else SearchDetailEvent.OpenQuality,
                )
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl.ifBlank { selected.url }
                _state.update {
                    it.copy(busy = false, busyAction = null, needsVerification = true)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        busyAction = null,
                        error = error.message ?: "Gagal menyiapkan unduhan.",
                    )
                }
            }
        }
    }

    fun subscribe(lastEpisodeText: String) {
        val selected = result ?: return
        val lastEpisode = lastEpisodeText.trim().toIntOrNull()
        if (lastEpisode == null) {
            _state.update { it.copy(error = "Masukkan episode terakhir, gunakan 0 jika belum ada.") }
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyAction = "subscribe", error = null) }
            try {
                val subscription = addSubscription(selected.url, lastEpisode.coerceAtLeast(0))
                _state.update {
                    it.copy(
                        busy = false,
                        busyAction = null,
                        message = "${subscription.name} masuk ke Langganan.",
                    )
                }
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl.ifBlank { selected.url }
                _state.update {
                    it.copy(busy = false, busyAction = null, needsVerification = true)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        busyAction = null,
                        error = error.message ?: "Gagal menambah langganan.",
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
