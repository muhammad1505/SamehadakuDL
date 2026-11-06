package app.samehadaku.dl.presentation.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.download.DownloadQueue
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.usecase.DeleteDownloadUseCase
import app.samehadaku.dl.domain.usecase.DownloadControlUseCase
import app.samehadaku.dl.domain.usecase.OpenDownloadUseCase
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
    settings: SettingsRepository,
    private val control: DownloadControlUseCase,
    private val openDownload: OpenDownloadUseCase,
    private val deleteDownload: DeleteDownloadUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(QueueTab.ALL)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val duplicateNotified = mutableSetOf<String>()

    init {
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
    }

    val state: StateFlow<QueueUiState> = combine(
        queue.tasks,
        settings.concurrency,
        query,
        tab,
    ) { tasks, concurrency, currentQuery, currentTab ->
        val visible = tasks
            .filter { matchesTab(it, currentTab) && matchesQuery(it, currentQuery) }
            .sortedWith(
                compareBy<DownloadTask> { it.state.isFinished }
                    .thenByDescending { it.createdAt },
            )
        QueueUiState(
            tasks = visible,
            allTasks = tasks,
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
        viewModelScope.launch {
            deleteDownload(task)
                .onSuccess {
                    control.remove(task.id)
                    _messages.send("File episode ${task.episodeNumber} dihapus.")
                }
                .onFailure { error ->
                    _messages.send(error.message ?: "File gagal dihapus.")
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

    private companion object {
        const val DUPLICATE_NOTICE_WINDOW_MS = 60_000L
    }
}
