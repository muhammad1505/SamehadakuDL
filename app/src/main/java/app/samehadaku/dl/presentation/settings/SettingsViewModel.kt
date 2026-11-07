package app.samehadaku.dl.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.model.VideoFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val treeUri: String? = null,
    val concurrency: Int = 1,
    val qualityDowngrade: Boolean = false,
    val quality: QualityPreference? = null,
    val autoCheckEnabled: Boolean = true,
    val autoCheckIntervalHours: Int = 24,
    val failedRetryEnabled: Boolean = true,
    val lastAutoCheckAt: Long = 0L,
    val sessionActive: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val cloudflareSession: CloudflareSession,
    private val autoCheckScheduler: AutoCheckScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { cloudflareSession.restore() }
            cloudflareSession.active.collect { active ->
                _state.update { it.copy(sessionActive = active) }
            }
        }
        viewModelScope.launch {
            settings.qualityPreference.collect { preference ->
                _state.update { it.copy(quality = preference) }
            }
        }
        viewModelScope.launch {
            combine(
                combine(
                    settings.downloadTreeUri,
                    settings.concurrency,
                    settings.qualityDowngrade,
                    settings.autoCheckEnabled,
                    settings.autoCheckIntervalHours,
                ) { treeUri, concurrency, downgrade, autoEnabled, autoHours ->
                    SettingsValues(treeUri, concurrency, downgrade, autoEnabled, autoHours)
                },
                settings.failedRetryEnabled,
                settings.lastAutoCheckAt,
            ) { values, retryEnabled, lastCheck ->
                Triple(values, retryEnabled, lastCheck)
            }.collect { (values, retryEnabled, lastCheck) ->
                _state.update {
                    it.copy(
                        treeUri = values.treeUri,
                        concurrency = values.concurrency,
                        qualityDowngrade = values.downgrade,
                        autoCheckEnabled = values.autoEnabled,
                        autoCheckIntervalHours = values.autoHours,
                        failedRetryEnabled = retryEnabled,
                        lastAutoCheckAt = lastCheck,
                        loading = false,
                    )
                }
            }
        }
    }

    fun setFolder(uri: String) {
        viewModelScope.launch {
            runCatching { settings.setDownloadTreeUri(uri) }
                .onSuccess {
                    _state.update { it.copy(message = "Folder tujuan disimpan.") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = "Gagal menyimpan folder: ${error.message}")
                    }
                }
        }
    }

    fun clearFolder() {
        viewModelScope.launch {
            runCatching { settings.setDownloadTreeUri(null) }
                .onSuccess {
                    _state.update {
                        it.copy(message = "Folder default digunakan (Download/SamehadakuDL).")
                    }
                }
        }
    }

    fun setConcurrency(value: Int) {
        viewModelScope.launch {
            settings.setConcurrency(value)
        }
    }

    fun setQualityDowngrade(enabled: Boolean) {
        viewModelScope.launch {
            settings.setQualityDowngrade(enabled)
        }
    }

    fun setQualityFormat(format: VideoFormat) {
        updateQuality { it.copy(format = format) }
    }

    fun setQualityResolution(resolution: String) {
        updateQuality { it.copy(resolution = resolution) }
    }

    fun setQualityHost(host: Host) {
        updateQuality { it.copy(host = host) }
    }

    fun clearQualityPreference() {
        viewModelScope.launch {
            settings.clearQualityPreference()
            _state.update { it.copy(message = "Kualitas unduhan kembali otomatis.") }
        }
    }

    private fun updateQuality(transform: (QualityPreference) -> QualityPreference) {
        viewModelScope.launch {
            val updated = transform(_state.value.quality ?: DEFAULT_QUALITY)
            runCatching {
                settings.saveQualityPreference(updated.format, updated.resolution, updated.host)
            }.onSuccess {
                _state.update {
                    it.copy(
                        quality = updated,
                        message = "Kualitas unduhan: ${updated.format.label} • " +
                            "${updated.resolution} • ${updated.host.label}.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(message = "Gagal menyimpan kualitas: ${error.message}")
                }
            }
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoCheckEnabled(enabled)
            runCatching { autoCheckScheduler.sync() }
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "Cek episode baru otomatis diaktifkan."
                    } else {
                        "Cek episode baru otomatis dimatikan."
                    },
                )
            }
        }
    }

    fun setAutoCheckInterval(hours: Int) {
        viewModelScope.launch {
            settings.setAutoCheckIntervalHours(hours)
            runCatching { autoCheckScheduler.sync() }
            _state.update { it.copy(message = "Cek otomatis dijadwalkan setiap $hours jam.") }
        }
    }

    fun setFailedRetryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setFailedRetryEnabled(enabled)
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "Percobaan ulang otomatis diaktifkan."
                    } else {
                        "Percobaan ulang otomatis dimatikan."
                    },
                )
            }
        }
    }

    fun runAutoCheckNow() {
        autoCheckScheduler.runNow()
        _state.update { it.copy(message = "Cek episode baru dijalankan di latar belakang.") }
    }

    /** Re-reads the persisted session when the screen becomes visible again. */
    fun refreshSession() {
        viewModelScope.launch {
            runCatching { cloudflareSession.restore() }
        }
    }

    fun resetCloudflareSession() {
        viewModelScope.launch {
            cloudflareSession.clear()
            _state.update {
                it.copy(
                    sessionActive = false,
                    message = "Sesi Cloudflare dihapus.",
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private data class SettingsValues(
        val treeUri: String?,
        val concurrency: Int,
        val downgrade: Boolean,
        val autoEnabled: Boolean,
        val autoHours: Int,
    )

    private companion object {
        val DEFAULT_QUALITY = QualityPreference(
            format = VideoFormat.MKV,
            resolution = "720p",
            host = Host.PIXELDRAIN,
        )
    }
}
