package app.samehadaku.dl.presentation.smartstorage

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.data.smartstorage.SmartStorageManager
import app.samehadaku.dl.domain.model.CleanupIssue
import app.samehadaku.dl.domain.model.PendingPlayback
import app.samehadaku.dl.domain.model.WatchedEpisode
import app.samehadaku.dl.domain.usecase.DeleteWatchedFileResult
import app.samehadaku.dl.domain.usecase.DeleteWatchedFileUseCase
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

/** One watched episode with everything the detail screen needs to explain it. */
data class SmartStorageEntry(
    val folderName: String,
    val fileName: String,
    val size: Long,
    val watchedAt: Long,
    val manual: Boolean,
    val dueAt: Long,
    val due: Boolean,
    val issue: CleanupIssue?,
) {
    val key: String get() = SmartStoragePolicy.key(folderName, fileName)
}

data class SmartStorageDetailState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val graceDays: Int = SmartStoragePolicy.defaultGraceDays,
    val entries: List<SmartStorageEntry> = emptyList(),
    val pending: List<PendingPlayback> = emptyList(),
    val busyKey: String? = null,
) {
    val dueCount: Int get() = entries.count { it.due && it.issue == null }
    val waitingCount: Int get() = entries.count { !it.due && it.issue == null }
    val issueCount: Int get() = entries.count { it.issue != null }
    val totalBytes: Long get() = entries.sumOf { it.size }
}

@HiltViewModel
class SmartStorageDetailViewModel @Inject constructor(
    private val smartStorage: SmartStorageManager,
    private val deleteWatchedFile: DeleteWatchedFileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SmartStorageDetailState())
    val state: StateFlow<SmartStorageDetailState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** System confirmation requests raised while deleting a file. */
    private val _confirmationRequests = Channel<IntentSender>(Channel.BUFFERED)
    val confirmationRequests = _confirmationRequests.receiveAsFlow()

    private var lastDelete: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            combine(
                smartStorage.settings,
                smartStorage.watchedEpisodes,
                smartStorage.cleanupIssues,
                smartStorage.pendingPlayback,
            ) { settings, watched, issues, pending ->
                buildState(settings.enabled, settings.graceDays, watched, issues, pending)
            }.collect { next ->
                _state.update { current -> next.copy(busyKey = current.busyKey) }
            }
        }
    }

    private fun buildState(
        enabled: Boolean,
        graceDays: Int,
        watched: List<WatchedEpisode>,
        issues: List<CleanupIssue>,
        pending: List<PendingPlayback>,
    ): SmartStorageDetailState {
        val issuesByKey = issues.associateBy { issue ->
            SmartStoragePolicy.key(issue.folderName, issue.fileName)
        }
        val entries = watched.map { entry ->
            val dueAt = SmartStoragePolicy.dueAt(entry.watchedAt, graceDays)
            val issue = issuesByKey[SmartStoragePolicy.key(entry.folderName, entry.fileName)]
            SmartStorageEntry(
                folderName = entry.folderName,
                fileName = entry.fileName,
                size = entry.size,
                watchedAt = entry.watchedAt,
                manual = entry.manual,
                dueAt = dueAt,
                due = SmartStoragePolicy.isDue(
                    watchedAt = entry.watchedAt,
                    now = System.currentTimeMillis(),
                    graceDays = graceDays,
                ),
                issue = issue,
            )
        }.sortedWith(
            compareBy<SmartStorageEntry> { it.issue == null }
                .thenBy { it.dueAt },
        )
        return SmartStorageDetailState(
            loading = false,
            enabled = enabled,
            graceDays = graceDays,
            entries = entries,
            pending = pending,
        )
    }

    fun unmark(entry: SmartStorageEntry) {
        viewModelScope.launch {
            smartStorage.unmarkWatched(entry.folderName, entry.fileName)
            _messages.send("Tanda tonton ${entry.fileName} dihapus.")
        }
    }

    fun deleteNow(entry: SmartStorageEntry) {
        lastDelete = entry.folderName to entry.fileName
        viewModelScope.launch { performDelete(entry.folderName, entry.fileName) }
    }

    /** Re-runs the last delete after the system dialog was approved. */
    fun retryLastDelete() {
        val (folderName, fileName) = lastDelete ?: return
        viewModelScope.launch { performDelete(folderName, fileName) }
    }

    private suspend fun performDelete(folderName: String, fileName: String) {
        val key = SmartStoragePolicy.key(folderName, fileName)
        _state.update { it.copy(busyKey = key) }
        val result = runCatching { deleteWatchedFile(folderName, fileName) }
            .getOrElse { error ->
                DeleteWatchedFileResult.Failed(
                    fileName,
                    error.message ?: "Gagal menghapus file.",
                )
            }
        _state.update { it.copy(busyKey = null) }
        when (result) {
            is DeleteWatchedFileResult.Deleted ->
                _messages.send("${result.fileName} dihapus.")

            is DeleteWatchedFileResult.AlreadyGone ->
                _messages.send("${result.fileName} sudah tidak ada di penyimpanan.")

            is DeleteWatchedFileResult.NeedsConfirmation -> {
                _confirmationRequests.send(result.sender)
                _messages.send("Butuh konfirmasi sistem untuk menghapus ${result.fileName}.")
            }

            is DeleteWatchedFileResult.Failed -> _messages.send(result.message)
        }
    }

}
