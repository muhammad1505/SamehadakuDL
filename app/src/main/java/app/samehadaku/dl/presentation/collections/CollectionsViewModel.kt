package app.samehadaku.dl.presentation.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.download.StoredFile
import app.samehadaku.dl.data.download.StoredFolder
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
    private val openDownload: OpenDownloadUseCase,
    private val shareDownload: ShareDownloadUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

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
                it.copy(loading = false, folders = folders, allFolders = folders, location = location)
            }
            applyQuery()
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
        viewModelScope.launch {
            val removed = runCatching { storage.deleteStoredFile(folder.name, file.name) }
                .getOrDefault(false)
            if (removed) {
                _messages.send("File ${file.name} dihapus.")
            } else {
                _messages.send(
                    "File tidak bisa dihapus. Mungkin sudah dipindahkan atau izin penyimpanan dicabut.",
                )
            }
            refresh()
        }
    }

    fun deleteFolder(folder: StoredFolder) {
        viewModelScope.launch {
            val deleted = runCatching { storage.deleteFolder(folder.name) }.getOrDefault(0)
            if (deleted > 0) {
                _messages.send(
                    "Folder ${folder.name} dihapus ($deleted file).",
                )
            } else {
                _messages.send(
                    "Folder tidak bisa dihapus. Periksa izin penyimpanan lalu coba lagi.",
                )
            }
            refresh()
        }
    }
}
