package app.samehadaku.dl.presentation.queue

import android.content.IntentSender
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.core.SessionStore
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.repository.AnimeCoverRepository
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.usecase.AutoRetryFailedUseCase
import app.samehadaku.dl.domain.usecase.DeleteDownloadUseCase
import app.samehadaku.dl.domain.usecase.DownloadControlUseCase
import app.samehadaku.dl.domain.usecase.OpenDownloadUseCase
import app.samehadaku.dl.domain.usecase.ShareDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QueueTab(val label: String) {
    ALL("Semua"),
    ACTIVE("Berjalan"),
    DONE("Selesai"),
    FAILED("Gagal"),
}

data class QueueUiState(
    val tasks: List<DownloadTask> = emptyList(),
    val allTasks: List<DownloadTask> = emptyList(),
    val covers: Map<String, Bitmap?> = emptyMap(),
    val concurrency: Int = 1,
    val query: String = "",
    val tab: QueueTab = QueueTab.ALL,
) {
    val totalCount: Int get() = allTasks.size
    val activeCount: Int get() = allTasks.count { it.state.isActive }
    val pausedCount: Int get() = allTasks.count { it.state == DownloadState.PAUSED }
    val doneCount: Int get() = allTasks.count { it.state == DownloadState.DONE }
    val failedCount: Int get() = allTasks.count {
        it.state == DownloadState.FAILED || it.state == DownloadState.SKIPPED
    }
    val runningCount: Int get() = allTasks.count { it.state.isActive }
    val queuedCount: Int get() = allTasks.count { it.state == DownloadState.QUEUED }
    val finishedCount: Int get() = allTasks.count { it.state.isFinished }
    val hasActive: Boolean get() = activeCount > 0
    val hasPaused: Boolean get() = pausedCount > 0
    val hasFailed: Boolean get() = failedCount > 0
    val hasFinished: Boolean get() = finishedCount > 0
    val totalBytes: Long get() = allTasks.sumOf { it.totalBytes }
    val doneBytes: Long get() = allTasks.sumOf { it.bytesDone }
    val overallProgress: Float
        get() = if (totalBytes > 0L) {
            (doneBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queue: DownloadQueue,
    private val coverRepository: AnimeCoverRepository,
    settings: SettingsRepository,
    private val control: DownloadControlUseCase,
    private val openDownload: OpenDownloadUseCase,
    private val shareDownload: ShareDownloadUseCase,
    private val deleteDownload: DeleteDownloadUseCase,
    private val autoRetryFailed: AutoRetryFailedUseCase,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(QueueTab.ALL)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** System confirmation requests raised by MediaStore while deleting. */
    private val _confirmationRequests = Channel<IntentSender>(Channel.BUFFERED)
    val confirmationRequests = _confirmationRequests.receiveAsFlow()

    /** Last file the user asked to remove; kept so it can be retried after approval. */
    private var pendingDelete: DownloadTask? = null

    private val duplicateNotified = mutableSetOf<String>()
    private val covers = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())

    init {
        viewModelScope.launch {
            // Report from the enqueue screen: how many links were dead up front.
            sessionStore.queueNotice?.let { notice ->
                sessionStore.queueNotice = null
                _messages.send(notice)
            }
            val retried = runCatching { autoRetryFailed() }.getOrDefault(0)
            if (retried > 0) {
                _messages.send("$retried unduhan gagal dicoba ulang otomatis.")
            }
        }
        viewModelScope.launch {
            queue.tasks.collect { tasks ->
                val recentThreshold = System.currentTimeMillis() - DUPLICATE_NOTICE_WINDOW_MS
                tasks.filter {
                    it.alreadyExisted &&
                        it.state == DownloadState.DONE &&
                        (it.finishedAt ?: 0L) >= recentThreshold
                }.forEach { task ->
                        if (duplicateNotified.add(task.id)) {
                            _messages.send(
                                "Episode ${task.episodeNumber} sudah ada di penyimpanan, " +
                                    "tidak diunduh ulang.",
                            )
                        }
                    }
            }
        }
        viewModelScope.launch {
            queue.tasks.collect { tasks ->
                val slugs = tasks.map { it.animeSlug }.distinct()
                covers.update { current -> current.filterKeys { it in slugs } }
                slugs.forEach { slug ->
                    if (slug !in covers.value) {
                        viewModelScope.launch {
                            val cover = coverRepository.load(slug)
                            covers.update { current -> current + (slug to cover) }
                        }
                    }
                }
            }
        }
    }

    val state: StateFlow<QueueUiState> = combine(
        queue.tasks,
        settings.concurrency,
        query,
        tab,
        covers,
    ) { tasks, concurrency, currentQuery, currentTab, currentCovers ->
        val visible = tasks
            .filter { matchesTab(it, currentTab) && matchesQuery(it, currentQuery) }
            .sortedWith(
                compareBy<DownloadTask> { it.state.isFinished }
                    .thenByDescending { it.createdAt },
            )
        QueueUiState(
            tasks = visible,
            allTasks = tasks,
            covers = currentCovers,
            concurrency = concurrency,
            query = currentQuery,
            tab = currentTab,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QueueUiState(),
    )

    private fun matchesTab(task: DownloadTask, tab: QueueTab): Boolean = when (tab) {
        QueueTab.ALL -> true
        QueueTab.ACTIVE -> !task.state.isFinished
        QueueTab.DONE -> task.state == DownloadState.DONE
        QueueTab.FAILED ->
            task.state == DownloadState.FAILED || task.state == DownloadState.SKIPPED
    }

    private fun matchesQuery(task: DownloadTask, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase()
        val haystack = buildString {
            append(task.animeSlug)
            append(' ')
            append(task.episodeSlug)
            append(' ')
            append(task.episodeNumber)
            append(' ')
            append(task.format.label)
            append(' ')
            append(task.resolution)
            append(' ')
            append(task.currentHost?.label.orEmpty())
        }.lowercase()
        return needle in haystack
    }

    fun onQueryChange(value: String) {
        query.update { value }
    }

    fun onTabSelected(index: Int) {
        tab.update { QueueTab.entries.getOrElse(index) { QueueTab.ALL } }
    }

    fun pauseAll() = control.pauseAll()
    fun resumeAll() = control.resumeAll()
    fun cancelAll() = control.cancelAll()
    fun clearFinished() = control.clearFinished()
    fun retryFailed() = control.retryFailed()

    fun retry(id: String) = control.retry(id)
    fun cancel(id: String) = control.cancel(id)
    fun pause(id: String) = control.pause(id)
    fun resume(id: String) = control.resume(id)
    fun remove(id: String) = control.remove(id)

    fun deleteFile(task: DownloadTask) {
        pendingDelete = task
        viewModelScope.launch { performDelete(task) }
    }

    /** Re-runs the last deletion after the user approves the system dialog. */
    fun retryPendingDelete() {
        val task = pendingDelete ?: return
        viewModelScope.launch { performDelete(task) }
    }

    private suspend fun performDelete(task: DownloadTask) {
        val result = deleteDownload(task).getOrElse { error ->
            _messages.send(error.message ?: "File gagal dihapus.")
            return
        }
        when {
            result.confirmation != null -> {
                _confirmationRequests.trySend(result.confirmation)
                _messages.send(
                    "Butuh konfirmasi sistem untuk menghapus file episode ${task.episodeNumber}.",
                )
            }
            result.denied > 0 -> _messages.send(
                "File episode ${task.episodeNumber} tidak bisa dihapus. " +
                    "Penyimpanan menolak izin tulis.",
            )
            else -> {
                pendingDelete = null
                control.remove(task.id)
                _messages.send("File episode ${task.episodeNumber} dihapus.")
            }
        }
    }

    fun open(task: DownloadTask) {
        viewModelScope.launch {
            openDownload(task).onFailure { error ->
                _messages.send(error.message ?: "Tidak bisa membuka file.")
            }
        }
    }

    fun share(task: DownloadTask) {
        val fileName = task.fileName ?: return
        viewModelScope.launch {
            shareDownload(task.animeSlug, fileName).onFailure { error ->
                _messages.send(error.message ?: "Tidak bisa membagikan file.")
            }
        }
    }

    private companion object {
        const val DUPLICATE_NOTICE_WINDOW_MS = 60_000L
    }
}
