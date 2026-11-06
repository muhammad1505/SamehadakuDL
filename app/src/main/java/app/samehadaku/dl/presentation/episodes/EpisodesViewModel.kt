package app.samehadaku.dl.presentation.episodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.RangeParser
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Subscription
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodesUiState(
    val animeSlug: String = "",
    val episodes: List<Episode> = emptyList(),
    val selectedUrls: Set<String> = emptySet(),
    val rangeText: String = "",
    val isSubscribed: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

sealed interface EpisodesEvent {
    data object OpenQuality : EpisodesEvent
}

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val subscriptions: SubscriptionRepository,
    private val queue: DownloadQueue,
) : ViewModel() {

    private val detection = sessionStore.detection
    private val episodes = detection?.episodes.orEmpty()
    private val animeSlug = detection?.animeSlug.orEmpty()

    private val _state = MutableStateFlow(
        EpisodesUiState(
            animeSlug = animeSlug,
            episodes = episodes,
            selectedUrls = episodes.map { it.url }.toSet(),
        ),
    )
    val state: StateFlow<EpisodesUiState> = _state.asStateFlow()

    private val _events = Channel<EpisodesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            subscriptions.subscriptions.collect { items ->
                val subscribed = items.any { it.slug.equals(animeSlug, ignoreCase = true) }
                _state.update { it.copy(isSubscribed = subscribed) }
            }
        }
    }

    fun toggleEpisode(url: String) {
        _state.update { current ->
            val selected = current.selectedUrls.toMutableSet()
            if (!selected.add(url)) selected.remove(url)
            current.copy(selectedUrls = selected, error = null)
        }
    }

    fun selectAll() {
        _state.update { current ->
            val allSelected = current.selectedUrls.size == current.episodes.size
            current.copy(
                selectedUrls = if (allSelected) emptySet() else current.episodes.map { it.url }.toSet(),
                error = null,
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedUrls = emptySet(), error = null) }
    }

    fun onRangeTextChange(value: String) {
        _state.update { it.copy(rangeText = value, error = null) }
    }

    fun applyRange() {
        val current = _state.value
        val indices = RangeParser.parse(current.rangeText, current.episodes.size)
        if (indices.isEmpty()) {
            _state.update { it.copy(error = "Rentang episode tidak valid.") }
            return
        }
        val selected = indices.map { index -> current.episodes[index - 1].url }.toSet()
        _state.update { it.copy(selectedUrls = selected, error = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** Follow/unfollow this anime, seeding the last episode from finished downloads. */
    fun toggleSubscription() {
        val current = _state.value
        val detection = detection ?: return
        val slug = current.animeSlug.ifBlank { detection.animeSlug }
        if (slug.isBlank()) return
        viewModelScope.launch {
            if (current.isSubscribed) {
                subscriptions.remove(slug)
                _state.update { it.copy(notice = "Anime ini berhenti diikuti.") }
                return@launch
            }
            val alreadyDownloaded = queue.tasks.value
                .filter { it.animeSlug.equals(slug, ignoreCase = true) }
                .filter { it.state == DownloadState.DONE }
                .maxOfOrNull { it.episodeNumber }
                ?: 0
            val url = if (detection.isListPage) detection.finalUrl else detection.sourceUrl
            subscriptions.addOrUpdate(
                Subscription(
                    name = Names.fromSlug(slug),
                    animeUrl = UrlUtils.normalizeUrl(url),
                    slug = slug,
                    lastEpisode = alreadyDownloaded,
                ),
            )
            _state.update {
                it.copy(
                    notice = if (alreadyDownloaded > 0) {
                        "Anime diikuti (terakhir Ep $alreadyDownloaded). Cek episode baru di menu Langganan."
                    } else {
                        "Anime diikuti. Cek episode baru lewat menu Langganan di Beranda."
                    },
                )
            }
        }
    }

    fun continueToQuality() {
        val current = _state.value
        if (current.selectedUrls.isEmpty()) {
            _state.update { it.copy(error = "Pilih minimal satu episode.") }
            return
        }
        val selectedEpisodes = current.episodes.filter { it.url in current.selectedUrls }
        sessionStore.selectedEpisodes = selectedEpisodes
        viewModelScope.launch { _events.send(EpisodesEvent.OpenQuality) }
    }
}
