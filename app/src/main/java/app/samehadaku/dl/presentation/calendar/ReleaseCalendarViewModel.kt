package app.samehadaku.dl.presentation.calendar

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.repository.AnimeCoverRepository
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import app.samehadaku.dl.domain.repository.SiteRepository
import app.samehadaku.dl.domain.usecase.AddSubscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseCalendarUiState(
    val loading: Boolean = true,
    val items: List<ReleaseScheduleItem> = emptyList(),
    val covers: Map<String, Bitmap?> = emptyMap(),
    val ratings: Map<String, String> = emptyMap(),
    val subscribedSlugs: Set<String> = emptySet(),
    val favoriteBusyUrl: String? = null,
    val actionNeedsVerification: Boolean = false,
    val error: String? = null,
    val needsVerification: Boolean = false,
    val message: String? = null,
)

sealed interface ReleaseCalendarEvent {
    data object OpenDetail : ReleaseCalendarEvent
}

/** Stable key for per-anime cover/rating state, including cards without a URL. */
internal fun releaseScheduleKey(item: ReleaseScheduleItem): String =
    item.url?.takeIf(String::isNotBlank) ?: "${item.day}|${item.title}"

@HiltViewModel
class ReleaseCalendarViewModel @Inject constructor(
    private val repository: SiteRepository,
    private val coverRepository: AnimeCoverRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val addSubscription: AddSubscriptionUseCase,
    private val sessionStore: SessionStore,
    private val cloudflareSession: CloudflareSession,
) : ViewModel() {

    private val _state = MutableStateFlow(ReleaseCalendarUiState())
    val state: StateFlow<ReleaseCalendarUiState> = _state.asStateFlow()

    private val _events = Channel<ReleaseCalendarEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            subscriptionRepository.subscriptions.collect { subscriptions ->
                _state.update {
                    it.copy(subscribedSlugs = subscriptions.map { sub -> sub.slug }.toSet())
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    needsVerification = false,
                    actionNeedsVerification = false,
                    covers = emptyMap(),
                    ratings = emptyMap(),
                )
            }
            try {
                val items = repository.loadReleaseSchedule()
                _state.update {
                    it.copy(
                        loading = false,
                        items = items,
                        covers = emptyMap(),
                        ratings = emptyMap(),
                    )
                }
                loadVisuals(items)
            } catch (verification: NeedsVerificationException) {
                _state.update {
                    it.copy(loading = false, needsVerification = true)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Jadwal anime belum bisa dimuat.",
                    )
                }
            }
        }
    }

    /** Opens the same detail screen used by search results. */
    fun openDetail(item: ReleaseScheduleItem) {
        val url = item.url?.takeIf(String::isNotBlank) ?: return
        val slug = scheduleSlug(item) ?: return
        sessionStore.selectedSearchResult = AnimeSearchResult(
            title = item.title,
            url = url,
            slug = slug,
            episodeLabel = item.episode,
        )
        viewModelScope.launch { _events.send(ReleaseCalendarEvent.OpenDetail) }
    }

    fun toggleFavorite(item: ReleaseScheduleItem) {
        val url = item.url?.takeIf(String::isNotBlank) ?: return
        val slug = scheduleSlug(item) ?: return
        if (_state.value.favoriteBusyUrl != null) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    favoriteBusyUrl = url,
                    message = null,
                    actionNeedsVerification = false,
                )
            }
            runCatching { cloudflareSession.restore() }
            try {
                if (slug in _state.value.subscribedSlugs) {
                    subscriptionRepository.remove(slug)
                    _state.update {
                        it.copy(
                            favoriteBusyUrl = null,
                            message = "${item.title} dihapus dari Langganan.",
                        )
                    }
                } else {
                    val subscription = addSubscription(url)
                    _state.update {
                        it.copy(
                            favoriteBusyUrl = null,
                            message = "${subscription.name} masuk ke Langganan.",
                        )
                    }
                }
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl.ifBlank { url }
                _state.update {
                    it.copy(
                        favoriteBusyUrl = null,
                        actionNeedsVerification = true,
                        message = "Selesaikan verifikasi Cloudflare lalu coba lagi.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        favoriteBusyUrl = null,
                        message = error.message ?: "Gagal memperbarui langganan.",
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun loadVisuals(items: List<ReleaseScheduleItem>) {
        items.forEach { item ->
            val key = releaseScheduleKey(item)
            viewModelScope.launch {
                // Some schedule cards point at a dead thumbnail or omit it. The
                // anime page still has the poster, so fall back to the slug.
                val fromCard = item.coverUrl?.let { url ->
                    coverRepository.loadImage(url, item.url)
                }
                val cover = fromCard ?: scheduleSlug(item)?.let { slug ->
                    coverRepository.load(slug)
                }
                _state.update { current ->
                    current.copy(covers = current.covers + (key to cover))
                }
            }
            if (item.rating.isNullOrBlank()) {
                val url = item.url ?: return@forEach
                viewModelScope.launch {
                    val rating = runCatching { repository.loadAnimeDetail(url) }
                        .getOrNull()
                        ?.rating
                        ?.takeIf(String::isNotBlank)
                    if (rating != null) {
                        _state.update { current ->
                            current.copy(ratings = current.ratings + (key to rating))
                        }
                    }
                }
            }
        }
    }

    private fun scheduleSlug(item: ReleaseScheduleItem): String? =
        item.url?.let { UrlUtils.lastPathSegment(it) }?.takeIf(String::isNotBlank)
}
