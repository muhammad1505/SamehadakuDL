package app.samehadaku.dl.presentation.search

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.data.repository.AnimeCoverRepository
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.repository.SiteRepository
import app.samehadaku.dl.domain.usecase.AddSubscriptionUseCase
import app.samehadaku.dl.domain.usecase.DetectEpisodesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val searched: Boolean = false,
    val results: List<AnimeSearchResult> = emptyList(),
    val covers: Map<String, Bitmap?> = emptyMap(),
    val error: String? = null,
    val needsVerification: Boolean = false,
    val busyUrl: String? = null,
    val busyAction: String? = null,
    val message: String? = null,
)

sealed interface SearchEvent {
    data object OpenDetail : SearchEvent
    data object OpenEpisodes : SearchEvent
    data object OpenQuality : SearchEvent
}

/**
 * Finds anime on the site and lets the user act on a result immediately: queue it
 * for download (episode picker) or follow it so new episodes are checked later.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SiteRepository,
    private val coverRepository: AnimeCoverRepository,
    private val detectEpisodes: DetectEpisodesUseCase,
    private val addSubscription: AddSubscriptionUseCase,
    private val settings: SettingsRepository,
    private val sessionStore: SessionStore,
    private val cloudflareSession: CloudflareSession,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null
    // Network calls cannot always be cancelled immediately. Keep a generation so
    // a slower response for an older query can never replace newer results.
    private var queryGeneration = 0

    fun onQueryChange(value: String) {
        queryGeneration++
        _state.update {
            it.copy(
                query = value,
                error = null,
                needsVerification = false,
                loading = false,
                searched = false,
                results = emptyList(),
                covers = emptyMap(),
            )
        }
        searchJob?.cancel()
        if (value.trim().length < MIN_QUERY_LENGTH) {
            _state.update {
                it.copy(loading = false, searched = false, results = emptyList(), covers = emptyMap())
            }
            return
        }
        val generation = queryGeneration
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(value, generation)
        }
    }

    fun submit() {
        searchJob?.cancel()
        val generation = queryGeneration
        viewModelScope.launch { runSearch(_state.value.query, generation) }
    }

    private suspend fun runSearch(rawQuery: String, generation: Int) {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) return
        if (generation != queryGeneration) return
        _state.update {
            it.copy(
                loading = true,
                searched = true,
                error = null,
                needsVerification = false,
            )
        }
        runCatching { cloudflareSession.restore() }
        try {
            val results = repository.searchAnime(query)
            if (generation != queryGeneration) return
            _state.update {
                it.copy(
                    loading = false,
                    results = results,
                    covers = emptyMap(),
                    error = if (results.isEmpty()) {
                        "Tidak ada anime yang cocok dengan \"$query\"."
                    } else {
                        null
                    },
                )
            }
            loadCovers(results, generation)
        } catch (verification: NeedsVerificationException) {
            if (generation != queryGeneration) return
            sessionStore.pendingVerificationUrl = verification.pageUrl
            _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    covers = emptyMap(),
                    needsVerification = true,
                    error = null,
                )
            }
        } catch (error: Exception) {
            if (generation != queryGeneration) return
            _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    covers = emptyMap(),
                    error = error.message ?: "Pencarian gagal. Coba lagi.",
                )
            }
        }
    }

    /** Opens the episode picker for this anime, exactly like pasting its URL. */
    fun download(result: AnimeSearchResult) {
        if (_state.value.busyUrl != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyUrl = result.url, busyAction = "download") }
            runCatching { cloudflareSession.restore() }
            try {
                val detection = detectEpisodes(result.url)
                if (detection.episodes.isEmpty()) {
                    throw IllegalStateException("Tidak ada episode yang terdeteksi.")
                }
                sessionStore.saveDetection(detection)
                settings.addRecentUrl(detection.sourceUrl)
                _state.update { it.copy(busyUrl = null, busyAction = null) }
                _events.send(
                    if (detection.isListPage) SearchEvent.OpenEpisodes else SearchEvent.OpenQuality,
                )
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl =
                    verification.pageUrl.ifBlank { result.url }
                _state.update {
                    it.copy(
                        busyUrl = null,
                        busyAction = null,
                        needsVerification = true,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busyUrl = null,
                        busyAction = null,
                        message = error.message ?: "Gagal menyiapkan unduhan.",
                    )
                }
            }
        }
    }

    fun openDetail(result: AnimeSearchResult) {
        sessionStore.selectedSearchResult = result
        viewModelScope.launch { _events.send(SearchEvent.OpenDetail) }
    }

    fun subscribe(result: AnimeSearchResult) {
        if (_state.value.busyUrl != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyUrl = result.url, busyAction = "subscribe") }
            runCatching { cloudflareSession.restore() }
            try {
                val subscription = addSubscription(result.url)
                _state.update {
                    it.copy(
                        busyUrl = null,
                        busyAction = null,
                        message = "${subscription.name} masuk ke Langganan.",
                    )
                }
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl =
                    verification.pageUrl.ifBlank { result.url }
                _state.update {
                    it.copy(
                        busyUrl = null,
                        busyAction = null,
                        needsVerification = true,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busyUrl = null,
                        busyAction = null,
                        message = error.message ?: "Gagal menambah langganan.",
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearQuery() {
        queryGeneration++
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                results = emptyList(),
                covers = emptyMap(),
                searched = false,
                error = null,
                loading = false,
            )
        }
    }

    private fun loadCovers(results: List<AnimeSearchResult>, generation: Int) {
        results.forEach { result ->
            viewModelScope.launch {
                val cover = coverRepository.load(result.slug)
                if (generation != queryGeneration) return@launch
                _state.update { current ->
                    current.copy(covers = current.covers + (result.slug to cover))
                }
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MS = 450L
    }
}
