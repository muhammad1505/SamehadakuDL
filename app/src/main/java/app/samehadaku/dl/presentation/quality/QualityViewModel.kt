package app.samehadaku.dl.presentation.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.EnqueueResult
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import app.samehadaku.dl.domain.usecase.EnqueueDownloadsUseCase
import app.samehadaku.dl.domain.usecase.LoadQualityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QualityUiState(
    val animeSlug: String = "",
    val episodes: List<Episode> = emptyList(),
    val selectedCount: Int = 0,
    val loading: Boolean = true,
    val starting: Boolean = false,
    val error: String? = null,
    val needsVerification: Boolean = false,
    val needsVerificationUrl: String? = null,
    val formats: List<VideoFormat> = emptyList(),
    val resolutions: List<String> = emptyList(),
    val hosts: List<Host> = emptyList(),
    val selectedFormat: VideoFormat? = null,
    val selectedResolution: String? = null,
    val selectedHost: Host? = null,
    val sampleEpisodeUrl: String? = null,
)

sealed interface QualityEvent {
    data object OpenQueue : QualityEvent
}

@HiltViewModel
class QualityViewModel @Inject constructor(
    private val loadQuality: LoadQualityUseCase,
    private val enqueueDownloads: EnqueueDownloadsUseCase,
    private val sessionStore: SessionStore,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val detection = sessionStore.detection
    private val episodes: List<Episode> =
        sessionStore.selectedEpisodes.ifEmpty { detection?.episodes.orEmpty() }

    private var links: DownloadLinks = DownloadLinks.EMPTY
    private var autoStartRequested = false

    private val _state = MutableStateFlow(
        QualityUiState(
            animeSlug = detection?.animeSlug.orEmpty(),
            episodes = episodes,
            selectedCount = episodes.size,
        ),
    )
    val state: StateFlow<QualityUiState> = _state.asStateFlow()

    private val _events = Channel<QualityEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    needsVerification = false,
                    needsVerificationUrl = null,
                )
            }
            try {
                val preview = loadQuality(episodes)
                links = preview.links
                // Prefer the quality used last time when it is still available.
                val saved = runCatching { settings.currentQualityPreference() }.getOrNull()
                val formats = preview.links.formats()
                val firstFormat = saved?.format?.takeIf { it in formats }
                    ?: formats.firstOrNull()
                val resolutions = firstFormat?.let { preview.links.resolutions(it) }.orEmpty()
                val firstResolution = saved?.resolution?.takeIf { it in resolutions }
                    ?: resolutions.firstOrNull()
                val hosts = if (firstFormat != null && firstResolution != null) {
                    preview.links.hosts(firstFormat, firstResolution)
                } else {
                    emptyList()
                }
                val firstHost = saved?.host?.takeIf { it in hosts } ?: hosts.firstOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        formats = formats,
                        resolutions = resolutions,
                        hosts = hosts,
                        selectedFormat = firstFormat,
                        selectedResolution = firstResolution,
                        selectedHost = firstHost,
                        sampleEpisodeUrl = preview.episodeUrl,
                    )
                }
                // Pengaturan adalah sumber kualitas untuk semua jalur unduhan. Jika
                // pengguna sudah memilihnya, langsung antrekan setelah tautan terbaca
                // sehingga layar ini tidak menjadi langkah ekstra di setiap menu.
                if (saved != null && firstFormat != null && firstResolution != null &&
                    firstHost != null && !autoStartRequested
                ) {
                    autoStartRequested = true
                    startDownload()
                }
            } catch (verification: NeedsVerificationException) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        needsVerification = true,
                        needsVerificationUrl = verification.pageUrl,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Gagal memuat kualitas.",
                    )
                }
            }
        }
    }

    fun selectFormat(format: VideoFormat) {
        val resolutions = links.resolutions(format)
        if (resolutions.isEmpty()) return
        val resolution = resolutions.first()
        _state.update {
            it.copy(
                selectedFormat = format,
                selectedResolution = resolution,
                resolutions = resolutions,
                hosts = links.hosts(format, resolution),
                selectedHost = links.hosts(format, resolution).firstOrNull(),
            )
        }
    }

    fun selectResolution(resolution: String) {
        val format = _state.value.selectedFormat ?: return
        val hosts = links.hosts(format, resolution)
        if (hosts.isEmpty()) return
        _state.update {
            it.copy(
                selectedResolution = resolution,
                hosts = hosts,
                selectedHost = hosts.firstOrNull(),
            )
        }
    }

    fun selectHost(host: Host) {
        _state.update { it.copy(selectedHost = host) }
    }

    private fun deadLinkNotice(result: EnqueueResult): String? {
        if (result.deadEpisodes.isEmpty()) return null
        val listed = result.deadEpisodes.take(6).joinToString(", ") { "Ep $it" }
        val extra = if (result.deadEpisodes.size > 6) {
            " +${result.deadEpisodes.size - 6} lain"
        } else {
            ""
        }
        return "${result.queued} dari ${result.total} episode diantrekan. " +
            "${result.deadCount} link mati ($listed$extra) dan akan dicoba ulang otomatis."
    }

    fun prepareVerification() {
        val target = _state.value.needsVerificationUrl
            ?: _state.value.sampleEpisodeUrl
            ?: detection?.sourceUrl
        sessionStore.pendingVerificationUrl = target
    }

    fun onVerifiedReturned() {
        _state.update { it.copy(needsVerification = false) }
        load()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun startDownload() {
        val current = _state.value
        val format = current.selectedFormat
        val resolution = current.selectedResolution
        val host = current.selectedHost
        if (format == null || resolution == null || host == null) {
            _state.update { it.copy(error = "Pilih format, resolusi, dan server terlebih dahulu.") }
            return
        }
        if (current.episodes.isEmpty()) {
            _state.update { it.copy(error = "Tidak ada episode yang dipilih.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(starting = true, error = null) }
            try {
                sessionStore.saveSelection(format, resolution, host)
                runCatching { settings.saveQualityPreference(format, resolution, host) }
                val result = enqueueDownloads(
                    episodes = current.episodes,
                    animeSlug = current.animeSlug.ifBlank { detection?.animeSlug.orEmpty() },
                    format = format,
                    resolution = resolution,
                    preferredHost = host,
                )
                sessionStore.queueNotice = deadLinkNotice(result)
                _state.update { it.copy(starting = false) }
                _events.send(QualityEvent.OpenQueue)
            } catch (verification: NeedsVerificationException) {
                _state.update {
                    it.copy(
                        starting = false,
                        needsVerification = true,
                        needsVerificationUrl = verification.pageUrl.ifBlank {
                            current.sampleEpisodeUrl ?: detection?.sourceUrl.orEmpty()
                        },
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        starting = false,
                        error = error.message ?: "Gagal memulai unduhan.",
                    )
                }
            }
        }
    }
}
