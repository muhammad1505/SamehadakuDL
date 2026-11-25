package app.samehadaku.dl.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.data.smartstorage.SmartStorageManager
import app.samehadaku.dl.data.smartstorage.SmartStorageScheduler
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.model.VideoFormat
import app.samehadaku.dl.domain.usecase.CleanWatchedFilesUseCase
import app.samehadaku.dl.domain.usecase.SmartStorageCleanupResult
import app.samehadaku.dl.presentation.util.formatBytes
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
    val qualityDowngrade: Boolean = true,
    val quality: QualityPreference? = null,
    val autoCheckEnabled: Boolean = true,
    val autoCheckIntervalMinutes: Int = 60,
    val failedRetryEnabled: Boolean = true,
    val historyAutoClearDays: Int = 0,
    val turboDownloads: Boolean = true,
    val lastAutoCheckAt: Long = 0L,
    val lastAutoCheckSuccessAt: Long = 0L,
    val smartStorageEnabled: Boolean = false,
    val smartStorageGraceDays: Int = 3,
    val smartStorageLimitGb: Int = 0,
    val smartWatchedCount: Int = 0,
    val smartWatchedBytes: Long = 0L,
    val smartDeletedCount: Int = 0,
    val smartReclaimedBytes: Long = 0L,
    val smartLastCleanupAt: Long = 0L,
    val smartCleaning: Boolean = false,
    val sessionActive: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val cloudflareSession: CloudflareSession,
    private val autoCheckScheduler: AutoCheckScheduler,
    private val smartStorage: SmartStorageManager,
    private val cleanWatchedFiles: CleanWatchedFilesUseCase,
    private val smartStorageScheduler: SmartStorageScheduler,
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
                    settings.autoCheckIntervalMinutes,
                ) { treeUri, concurrency, downgrade, autoEnabled, autoMinutes ->
                    SettingsValues(treeUri, concurrency, downgrade, autoEnabled, autoMinutes)
                },
                settings.failedRetryEnabled,
                settings.lastAutoCheckAt,
                settings.lastAutoCheckSuccessAt,
            ) { values, retryEnabled, lastCheck, lastSuccess ->
                SettingsCheckValues(values, retryEnabled, lastCheck, lastSuccess)
            }.collect { (values, retryEnabled, lastCheck, lastSuccess) ->
                _state.update {
                    it.copy(
                        treeUri = values.treeUri,
                        concurrency = values.concurrency,
                        qualityDowngrade = values.downgrade,
                        autoCheckEnabled = values.autoEnabled,
                        autoCheckIntervalMinutes = values.autoMinutes,
                        failedRetryEnabled = retryEnabled,
                        lastAutoCheckAt = lastCheck,
                        lastAutoCheckSuccessAt = lastSuccess,
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.historyAutoClearDays.collect { days ->
                _state.update { it.copy(historyAutoClearDays = days) }
            }
        }
        viewModelScope.launch {
            settings.turboDownloads.collect { enabled ->
                _state.update { it.copy(turboDownloads = enabled) }
            }
        }
        viewModelScope.launch {
            combine(
                smartStorage.settings,
                smartStorage.stats,
            ) { smart, stats -> smart to stats }
                .collect { (smart, stats) ->
                    _state.update {
                        it.copy(
                            smartStorageEnabled = smart.enabled,
                            smartStorageGraceDays = smart.graceDays,
                            smartStorageLimitGb = smart.maxStorageGb,
                            smartWatchedCount = stats.watchedCount,
                            smartWatchedBytes = stats.watchedBytes,
                            smartDeletedCount = stats.deletedCount,
                            smartReclaimedBytes = stats.reclaimedBytes,
                            smartLastCleanupAt = stats.lastCleanupAt,
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

    fun setAutoCheckInterval(minutes: Int) {
        viewModelScope.launch {
            settings.setAutoCheckIntervalMinutes(minutes)
            runCatching { autoCheckScheduler.sync() }
            _state.update {
                it.copy(message = "Cek otomatis dijadwalkan setiap ${intervalLabel(minutes)}.")
            }
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

    fun setTurboDownloads(enabled: Boolean) {
        viewModelScope.launch {
            settings.setTurboDownloads(enabled)
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "Unduhan turbo aktif: file besar diunduh lewat beberapa koneksi."
                    } else {
                        "Unduhan turbo dimatikan: setiap file memakai satu koneksi."
                    },
                )
            }
        }
    }

    fun setHistoryAutoClearDays(days: Int) {
        viewModelScope.launch {
            settings.setHistoryAutoClearDays(days)
            _state.update {
                it.copy(
                    message = if (days <= 0) {
                        "Auto clear riwayat unduhan dimatikan."
                    } else {
                        "Riwayat unduhan lebih lama dari $days hari dibersihkan otomatis " +
                            "saat aplikasi dibuka. File unduhan tetap tersimpan."
                    },
                )
            }
        }
    }

    fun setSmartStorageEnabled(enabled: Boolean) {
        viewModelScope.launch {
            smartStorage.setEnabled(enabled)
            runCatching { smartStorageScheduler.sync() }
            // Enabling the feature should not wait for the next app resume to
            // perform the first catch-up cleanup.
            if (enabled) runCatching { smartStorageScheduler.runNowIfDue() }
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "Smart Storage diaktifkan. Pembersihan otomatis menghapus episode " +
                            "tonton setelah ${it.smartStorageGraceDays} hari; tombol manual " +
                            "menghapus sekarang."
                    } else {
                        "Smart Storage dimatikan. Tidak ada file yang dihapus otomatis."
                    },
                )
            }
        }
    }

    fun setSmartStorageGraceDays(days: Int) {
        viewModelScope.launch {
            smartStorage.setGraceDays(days)
            _state.update {
                it.copy(message = "Masa tenggang pembersihan otomatis: ${days} hari.")
            }
        }
    }

    fun setSmartStorageLimitGb(gigabytes: Int) {
        viewModelScope.launch {
            smartStorage.setMaxStorageGb(gigabytes)
            _state.update {
                it.copy(
                    message = if (gigabytes <= 0) {
                        "Batas penyimpanan Smart Storage dimatikan."
                    } else {
                        "Batas penyimpanan Smart Storage: $gigabytes GB. Episode tonton " +
                            "tertua dihapus lebih awal bila totalnya melebihi batas."
                    },
                )
            }
        }
    }

    fun cleanWatchedNow() {
        if (!_state.value.smartStorageEnabled || _state.value.smartCleaning) return
        viewModelScope.launch {
            _state.update { it.copy(smartCleaning = true) }
            // The button is an explicit user action, so it must not be blocked by
            // the grace period that only applies to automatic background cleanup.
            val result = runCatching { cleanWatchedFiles(ignoreGrace = true) }
            _state.update { current ->
                current.copy(
                    smartCleaning = false,
                    message = result.fold(
                        onSuccess = ::cleanupMessage,
                        onFailure = { error ->
                            "Gagal membersihkan Smart Storage: ${error.message}"
                        },
                    ),
                )
            }
        }
    }

    fun runAutoCheckNow() {
        autoCheckScheduler.runNow()
        _state.update {
            it.copy(
                message = "Cek episode diminta. Pemeriksaan yang sedang berjalan tidak diulang.",
            )
        }
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
        val autoMinutes: Int,
    )

    private data class SettingsCheckValues(
        val values: SettingsValues,
        val retryEnabled: Boolean,
        val lastCheckAt: Long,
        val lastSuccessAt: Long,
    )

    private fun intervalLabel(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60} jam" else "$minutes menit"

    private fun cleanupMessage(result: SmartStorageCleanupResult): String {
        if (result.disabled) return "Aktifkan Smart Storage terlebih dahulu."
        val parts = buildList {
            if (result.deletedFiles > 0) {
                add(
                    "${result.deletedFiles} episode dihapus " +
                        "(${formatBytes(result.reclaimedBytes)}).",
                )
            }
            if (result.blockedFiles > 0) {
                add(
                    "${result.blockedFiles} file menunggu izin penyimpanan; " +
                        "hapus manual dari Koleksi bila perlu.",
                )
            }
            if (result.skippedFiles > 0) {
                add(
                    "${result.skippedFiles} file dilewati karena ukuran atau bentuk " +
                        "file tidak lagi cocok.",
                )
            }
            if (isEmpty()) {
                add("Belum ada episode yang ditandai tonton.")
            }
        }
        return parts.joinToString(" ")
    }

    private companion object {
        val DEFAULT_QUALITY = QualityPreference(
            format = VideoFormat.MKV,
            resolution = "720p",
            host = Host.PIXELDRAIN,
        )
    }
}
