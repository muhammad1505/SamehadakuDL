package app.samehadaku.dl.presentation.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.model.Subscription
import app.samehadaku.dl.domain.usecase.AddSubscriptionUseCase
import app.samehadaku.dl.domain.usecase.CheckSubscriptionsUseCase
import app.samehadaku.dl.domain.usecase.DetectEpisodesUseCase
import app.samehadaku.dl.domain.usecase.EnqueueDownloadsUseCase
import app.samehadaku.dl.domain.usecase.ImportSubscriptionsUseCase
import app.samehadaku.dl.domain.usecase.LoadQualityUseCase
import app.samehadaku.dl.domain.usecase.SubscriptionCheckResult
import app.samehadaku.dl.domain.usecase.SubscriptionFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shown as a modal when a check finds nothing new (or only failures). */
data class CheckReport(
    val upToDate: List<Subscription>,
    val failures: List<SubscriptionFailure>,
)

data class SubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val loading: Boolean = true,
    val checking: Boolean = false,
    val checkProgress: String = "",
    val result: SubscriptionCheckResult? = null,
    val checkReport: CheckReport? = null,
    val selectedSlugs: Set<String> = emptySet(),
    val quality: QualityPreference? = null,
    val qualityAutomatic: Boolean = false,
    val autoCheckEnabled: Boolean = true,
    val autoCheckIntervalHours: Int = 24,
    val enqueueing: Boolean = false,
    val addInProgress: Boolean = false,
    val addError: String? = null,
    val needsVerification: Boolean = false,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = subscriptions.isEmpty() && !loading

    val selectedEpisodeCount: Int
        get() = result?.checks
            ?.filter { it.subscription.slug in selectedSlugs }
            ?.sumOf { it.newEpisodes.size }
            ?: 0
}

sealed interface SubscriptionsEvent {
    data object Added : SubscriptionsEvent
    data object OpenEpisodes : SubscriptionsEvent
    data object OpenQueue : SubscriptionsEvent
}

/**
 * "Langganan" mirrors the Python downloader: follow anime, remember the last
 * downloaded episode, then check for and download only the newer episodes.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val checkSubscriptionsUseCase: CheckSubscriptionsUseCase,
    private val importSubscriptionsUseCase: ImportSubscriptionsUseCase,
    private val detectEpisodesUseCase: DetectEpisodesUseCase,
    private val enqueueDownloadsUseCase: EnqueueDownloadsUseCase,
    private val loadQuality: LoadQualityUseCase,
    private val settings: SettingsRepository,
    private val sessionStore: SessionStore,
    private val queue: DownloadQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    private val _events = Channel<SubscriptionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.subscriptions.collect { subscriptions ->
                _state.update { it.copy(subscriptions = subscriptions, loading = false) }
            }
        }
        viewModelScope.launch {
            settings.qualityPreference.collect { preference ->
                if (preference != null) {
                    _state.update { it.copy(quality = preference) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                settings.autoCheckEnabled,
                settings.autoCheckIntervalHours,
            ) { enabled, hours -> enabled to hours }
                .collect { (enabled, hours) ->
                    _state.update {
                        it.copy(
                            autoCheckEnabled = enabled,
                            autoCheckIntervalHours = hours,
                        )
                    }
                }
        }
    }

    fun addSubscription(url: String, lastEpisodeText: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _state.update { it.copy(addError = "Tempel URL halaman anime dulu.") }
            return
        }
        val lastEpisode = lastEpisodeText.trim().toIntOrNull() ?: 0
        viewModelScope.launch {
            _state.update { it.copy(addInProgress = true, addError = null) }
            try {
                val subscription = addSubscriptionUseCase(trimmedUrl, lastEpisode)
                _state.update {
                    it.copy(
                        addInProgress = false,
                        addError = null,
                        needsVerification = false,
                        message = "${subscription.name} ditambahkan ke langganan.",
                    )
                }
                _events.send(SubscriptionsEvent.Added)
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl
                _state.update {
                    it.copy(
                        addInProgress = false,
                        addError = "Perlu verifikasi Cloudflare dulu sebelum menambah langganan.",
                        needsVerification = true,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        addInProgress = false,
                        addError = error.message ?: "Gagal menambah langganan.",
                    )
                }
            }
        }
    }

    fun dismissAddError() {
        _state.update { it.copy(addError = null) }
    }

    fun checkForUpdates() {
        val current = _state.value
        if (current.checking) return
        if (current.subscriptions.isEmpty()) {
            _state.update { it.copy(message = "Belum ada langganan. Tambah anime dulu, ya.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    checking = true,
                    checkProgress = "Menyiapkan pemeriksaan…",
                    result = null,
                    checkReport = null,
                    needsVerification = false,
                )
            }
            try {
                val result = checkSubscriptionsUseCase { checked, total, name ->
                    _state.update {
                        it.copy(
                            checkProgress = if (name.isBlank()) {
                                "Menyelesaikan pemeriksaan…"
                            } else {
                                "Memeriksa ${checked + 1}/$total • $name"
                            },
                        )
                    }
                }
                val resolved = resolveQuality(result)
                val hasNewEpisodes = result.checks.isNotEmpty()
                _state.update {
                    it.copy(
                        checking = false,
                        checkProgress = "",
                        result = if (hasNewEpisodes) result else null,
                        checkReport = if (hasNewEpisodes) {
                            null
                        } else {
                            CheckReport(upToDate = result.upToDate, failures = result.failures)
                        },
                        selectedSlugs = result.checks.map { check -> check.subscription.slug }.toSet(),
                        quality = resolved?.preference ?: it.quality,
                        qualityAutomatic = resolved?.automatic == true,
                        message = null,
                    )
                }
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl
                _state.update {
                    it.copy(
                        checking = false,
                        checkProgress = "",
                        needsVerification = true,
                        message = "Perlu verifikasi Cloudflare untuk memeriksa episode baru.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        checkProgress = "",
                        message = error.message ?: "Gagal memeriksa langganan.",
                    )
                }
            }
        }
    }

    fun toggleCheck(slug: String) {
        _state.update { current ->
            val selected = current.selectedSlugs.toMutableSet()
            if (!selected.add(slug)) selected.remove(slug)
            current.copy(selectedSlugs = selected)
        }
    }

    fun toggleAllChecks() {
        _state.update { current ->
            val all = current.result?.checks?.map { it.subscription.slug }?.toSet().orEmpty()
            current.copy(
                selectedSlugs = if (current.selectedSlugs.size == all.size) emptySet() else all,
            )
        }
    }

    fun dismissResult() {
        _state.update { it.copy(result = null, selectedSlugs = emptySet()) }
    }

    fun dismissCheckReport() {
        _state.update { it.copy(checkReport = null) }
    }

    fun downloadSelected() {
        val current = _state.value
        val result = current.result ?: return
        val quality = current.quality
        if (quality == null) {
            _state.update {
                it.copy(message = "Kualitas belum tersedia. Periksa ulang langganan lalu coba lagi.")
            }
            return
        }
        val chosen = result.checks.filter { it.subscription.slug in current.selectedSlugs }
        if (chosen.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(enqueueing = true) }
            val queuedUrls = queue.tasks.value.map { it.episodeUrl }.toMutableSet()
            var queued = 0
            var skipped = 0
            var failed = 0
            var dead = 0
            for (check in chosen) {
                val fresh = check.newEpisodes.filterNot { it.url in queuedUrls }
                skipped += check.newEpisodes.size - fresh.size
                if (fresh.isEmpty()) continue
                try {
                    val result = enqueueDownloadsUseCase(
                        episodes = fresh,
                        animeSlug = check.subscription.slug,
                        format = quality.format,
                        resolution = quality.resolution,
                        preferredHost = quality.host,
                    )
                    queued += result.queued
                    dead += result.deadCount
                    queuedUrls += fresh.map { it.url }
                } catch (_: Exception) {
                    failed += fresh.size
                }
            }
            val parts = buildList {
                if (queued > 0) add("$queued episode diantrekan")
                if (dead > 0) add("$dead link mati, akan dicoba ulang otomatis")
                if (skipped > 0) add("$skipped sudah ada di antrean")
                if (failed > 0) add("$failed gagal diantrekan")
            }
            _state.update {
                it.copy(
                    enqueueing = false,
                    result = null,
                    selectedSlugs = emptySet(),
                    message = if (parts.isEmpty()) {
                        "Tidak ada episode yang bisa diantrekan."
                    } else {
                        parts.joinToString(" • ") + "."
                    },
                )
            }
            if (queued > 0) _events.send(SubscriptionsEvent.OpenQueue)
        }
    }

    fun openEpisodes(subscription: Subscription) {
        viewModelScope.launch {
            try {
                val detection = detectEpisodesUseCase(subscription.animeUrl)
                sessionStore.saveDetection(detection)
                runCatching {
                    repository.addOrUpdate(
                        subscription.copy(animeUrl = UrlUtils.normalizeUrl(detection.finalUrl)),
                    )
                }
                _events.send(SubscriptionsEvent.OpenEpisodes)
            } catch (verification: NeedsVerificationException) {
                sessionStore.pendingVerificationUrl = verification.pageUrl
                _state.update {
                    it.copy(
                        needsVerification = true,
                        message = "Perlu verifikasi Cloudflare untuk membuka episode.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(message = error.message ?: "Gagal membuka daftar episode.")
                }
            }
        }
    }

    fun setLastEpisode(subscription: Subscription, episodeNumber: Int) {
        viewModelScope.launch {
            runCatching { repository.setLastEpisode(subscription.slug, episodeNumber) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            message = "${subscription.name} sekarang di Ep $episodeNumber.",
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(message = "Gagal menyimpan episode terakhir.") }
                }
        }
    }

    fun removeSubscription(subscription: Subscription) {
        viewModelScope.launch {
            runCatching { repository.remove(subscription.slug) }
            _state.update { it.copy(message = "${subscription.name} berhenti diikuti.") }
        }
    }

    fun importFromScript(json: String) {
        viewModelScope.launch {
            try {
                val summary = importSubscriptionsUseCase(json)
                val parts = buildList {
                    if (summary.imported > 0) add("${summary.imported} baru")
                    if (summary.updated > 0) add("${summary.updated} diperbarui")
                    if (summary.skipped > 0) add("${summary.skipped} dilewati")
                }
                _state.update {
                    it.copy(
                        message = "Impor selesai: ${parts.joinToString(", ")}.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(message = error.message ?: "Gagal mengimpor langganan.")
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private data class ResolvedQuality(
        val preference: QualityPreference,
        val automatic: Boolean,
    )

    private suspend fun resolveQuality(result: SubscriptionCheckResult): ResolvedQuality? {
        val saved = runCatching { settings.currentQualityPreference() }.getOrNull()
        if (saved != null) return ResolvedQuality(saved, automatic = false)
        val sample = result.checks.firstOrNull()?.newEpisodes?.firstOrNull() ?: return null
        val preview = loadQuality(listOf(sample))
        val format = preview.links.formats().firstOrNull() ?: return null
        val resolution = preview.links.resolutions(format).firstOrNull() ?: return null
        val host = preview.links.hosts(format, resolution).firstOrNull() ?: return null
        val preference = QualityPreference(format, resolution, host)
        runCatching { settings.saveQualityPreference(format, resolution, host) }
        return ResolvedQuality(preference, automatic = true)
    }
}
