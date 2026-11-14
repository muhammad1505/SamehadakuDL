package app.samehadaku.dl.presentation.collections

import android.content.IntentSender
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.download.DeletionResult
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.download.StoredFile
import app.samehadaku.dl.data.download.StoredFolder
import app.samehadaku.dl.data.repository.AnimeCoverRepository
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.usecase.OpenDownloadUseCase
import app.samehadaku.dl.domain.usecase.ShareDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val loading: Boolean = true,
    val allFolders: List<StoredFolder> = emptyList(),
    val folders: List<StoredFolder> = emptyList(),
    val query: String = "",
    val location: String = "",
    val covers: Map<String, Bitmap?> = emptyMap(),
    val error: String? = null,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && query.isBlank()
    val hasNoMatch: Boolean get() = folders.isEmpty() && query.isNotBlank()
    val fileCount: Int get() = folders.sumOf { it.files.size }

    // Totals always describe the whole collection, not the filtered result, so the
    // summary stays stable while the user searches.
    val totalFolders: Int get() = allFolders.size
    val totalFiles: Int get() = allFolders.sumOf { it.files.size }
    val totalBytes: Long get() = allFolders.sumOf { folder -> folder.totalBytes }
    val latestModified: Long get() = allFolders.maxOfOrNull { folder -> folder.lastModified } ?: 0L
}

/**
 * The collection is built from files that really exist on disk (custom SAF folder,
 * MediaStore Downloads, or legacy app storage) instead of queue entries, so it stays
 * accurate even after the download history is cleared.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val storage: StorageWriter,
    private val coverRepository: AnimeCoverRepository,
    private val openDownload: OpenDownloadUseCase,
    private val shareDownload: ShareDownloadUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** System confirmation requests raised by MediaStore while deleting. */
    private val _confirmationRequests = Channel<IntentSender>(Channel.BUFFERED)
    val confirmationRequests = _confirmationRequests.receiveAsFlow()

    /** Raised when the delete failed only because the folder access was never picked. */
    private val _folderAccessRequests = Channel<Unit>(Channel.BUFFERED)
    val folderAccessRequests = _folderAccessRequests.receiveAsFlow()

    /** Last deletion the user asked for; kept so it can be retried after approval. */
    private var pendingDelete: PendingDelete? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val folders = runCatching { storage.scanCollections() }
                .getOrElse { error ->
                    _state.update { it.copy(error = error.message) }
                    emptyList()
                }
            val location = runCatching { storage.describeActiveLocation() }.getOrDefault("")
            _state.update {
                it.copy(
                    loading = false,
                    folders = folders,
                    allFolders = folders,
                    location = location,
                    covers = it.covers.filterKeys { key -> folders.any { folder -> folder.name == key } },
                )
            }
            applyQuery()
            folders.forEach { folder ->
                if (folder.name !in _state.value.covers) {
                    launch {
                        val cover = coverRepository.load(folder.name)
                        _state.update { current ->
                            current.copy(covers = current.covers + (folder.name to cover))
                        }
                    }
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.update { value }
        applyQuery()
    }

    private fun applyQuery() {
        val needle = query.value.trim().lowercase()
        val source = _state.value.allFolders
        val filtered = if (needle.isBlank()) {
            source
        } else {
            source.filter { folder ->
                folder.name.lowercase().contains(needle) ||
                    folder.files.any { file -> file.name.lowercase().contains(needle) }
            }
        }
        _state.update { it.copy(query = query.value, folders = filtered) }
    }

    fun openFile(folder: StoredFolder, file: StoredFile) {
        viewModelScope.launch {
            openDownload.open(folder.name, file.name).onFailure { error ->
                _messages.send(error.message ?: "Tidak bisa membuka file.")
            }
        }
    }

    fun openLatest(folder: StoredFolder) {
        val latest = folder.files.maxByOrNull { it.modifiedAt } ?: return
        openFile(folder, latest)
    }

    fun shareFile(folder: StoredFolder, file: StoredFile) {
        viewModelScope.launch {
            shareDownload(folder.name, file.name).onFailure { error ->
                _messages.send(error.message ?: "Tidak bisa membagikan file.")
            }
        }
    }

    fun shareLatest(folder: StoredFolder) {
        val latest = folder.files.maxByOrNull { it.modifiedAt } ?: return
        shareFile(folder, latest)
    }

    fun deleteFile(folder: StoredFolder, file: StoredFile) {
        val pending = PendingDelete.File(folder.name, file.name)
        pendingDelete = pending
        viewModelScope.launch { performDelete(pending) }
    }

    fun deleteFolder(folder: StoredFolder) {
        val pending = PendingDelete.Folder(folder.name)
        pendingDelete = pending
        viewModelScope.launch { performDelete(pending) }
    }

    /** Re-runs the last deletion after the user approves the system dialog. */
    fun retryPendingDelete() {
        val pending = pendingDelete ?: return
        viewModelScope.launch { performDelete(pending) }
    }

    /** Saves the folder picked from the delete prompt, then retries the deletion. */
    fun onFolderPicked(uri: String) {
        viewModelScope.launch {
            runCatching { settings.setDownloadTreeUri(uri) }
            _messages.send("Izin folder disimpan. Mencoba menghapus lagi…")
            retryPendingDelete()
        }
    }

    private suspend fun performDelete(pending: PendingDelete) {
        val result = when (pending) {
            is PendingDelete.File ->
                runCatching { storage.deleteStoredFile(pending.folderName, pending.fileName) }
                    .getOrDefault(DeletionResult(denied = 1))

            is PendingDelete.Folder ->
                runCatching { storage.deleteFolder(pending.folderName) }
                    .getOrDefault(DeletionResult(denied = 1))
        }
        when {
            result.confirmation != null -> {
                requestConfirmation(result.confirmation)
                _messages.send("Butuh konfirmasi sistem untuk menghapus ${pending.label}.")
            }
            result.denied > 0 -> {
                // Android 10 refuses media rows owned by an earlier installation and has
                // no confirmation intent for them, so a picked folder is the only way to
                // finish the delete. Keep the request pending until the user answers.
                // The stored URI alone does not prove the grant survived: uninstall,
                // cleared data or a deleted folder leaves a dead URI behind. Only a
                // folder pick can fix that, so ask again when access is gone.
                val canPickFolder = !runCatching { storage.hasFolderAccess() }
                    .getOrDefault(false)
                pendingDelete = if (canPickFolder) pending else null
                if (canPickFolder) {
                    _folderAccessRequests.trySend(Unit)
                } else {
                    _messages.send(
                        if (result.deleted > 0) {
                            "${pending.label} terhapus sebagian, ${result.denied} bagian masih " + "ditahan penyimpanan."
                        } else {
                            "${pending.label} tidak bisa dihapus. Penyimpanan menolak izin tulis."
                        },
                    )
                }
            }
            result.deleted > 0 -> {
                pendingDelete = null
                when (pending) {
                    is PendingDelete.File ->
                        _messages.send("File ${pending.fileName} dihapus.")

                    is PendingDelete.Folder ->
                        _messages.send(
                            "Folder ${prettyTitle(pending.folderName)} dihapus " +
                                "(${result.deleted} file).",
                        )
                }
            }
            else -> {
                pendingDelete = null
                _messages.send("${pending.label} sudah tidak ada di penyimpanan.")
            }
        }
        refresh()
    }

    private fun prettyTitle(name: String): String = name.replace('-', ' ')

    /** Launches the system confirmation dialog and retries once the user approves. */
    fun requestConfirmation(sender: IntentSender) {
        _confirmationRequests.trySend(sender)
    }
}

/** What the user asked to remove, kept so it can be retried after a system prompt. */
private sealed class PendingDelete {
    abstract val label: String

    data class File(val folderName: String, val fileName: String) : PendingDelete() {
        override val label: String get() = "File $fileName"
    }

    data class Folder(val folderName: String) : PendingDelete() {
        override val label: String get() = "Folder ${folderName.replace('-', ' ')}"
    }
}
