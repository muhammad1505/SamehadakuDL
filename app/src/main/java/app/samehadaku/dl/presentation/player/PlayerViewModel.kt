package app.samehadaku.dl.presentation.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.playback.PlaybackPositionStore
import app.samehadaku.dl.data.smartstorage.SmartStorageManager
import app.samehadaku.dl.presentation.util.prettyName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** One playable episode inside the collection folder the player was opened from. */
data class PlayerEntry(
    val fileName: String,
    val title: String,
    val uri: Uri,
    val size: Long,
    val resumeMs: Long,
)

data class PlayerUiState(
    val loading: Boolean = true,
    val folderLabel: String = "",
    val entries: List<PlayerEntry> = emptyList(),
    val index: Int = 0,
    val error: String? = null,
) {
    val current: PlayerEntry? get() = entries.getOrNull(index)
    val uri: Uri? get() = current?.uri
    val title: String get() = current?.title ?: folderLabel
    val resumeMs: Long get() = current?.resumeMs ?: 0L
    val size: Long get() = current?.size ?: 0L
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < entries.lastIndex
}

/**
 * Internal playback with real progress. Unlike the external-player heuristic,
 * the player itself reports the position, so an episode is marked watched only
 * after 90% of the video was actually played and the position is persisted for
 * "continue watching".
 *
 * The whole collection folder is loaded as a playlist, so the player can move to
 * the previous or next episode without leaving the screen and the user can jump
 * to any episode from the list.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val storage: StorageWriter,
    private val smartStorage: SmartStorageManager,
    private val positions: PlaybackPositionStore,
) : ViewModel() {

    private val folderName: String = savedStateHandle.get<String>("folder").orEmpty()
    private val fileName: String = savedStateHandle.get<String>("file").orEmpty()

    private val _state = MutableStateFlow(
        PlayerUiState(folderLabel = prettyName(folderName)),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var lastSavedPosition = 0L
    private var completed = false

    /** Latest position per episode, so jumping back within one session resumes correctly. */
    private val knownPositions = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        if (folderName.isBlank() || fileName.isBlank()) {
            _state.update { it.copy(loading = false, error = "Episode tidak dikenal.") }
            return
        }
        val entries = loadFolderEntries()
        if (entries.isEmpty()) {
            _state.update {
                it.copy(
                    loading = false,
                    error = "File tidak ditemukan. Mungkin sudah dipindahkan atau dihapus.",
                )
            }
            return
        }
        val startIndex = entries.indexOfFirst { it.fileName.equals(fileName, true) }
        if (startIndex < 0) {
            _state.update {
                it.copy(
                    loading = false,
                    error = "File tidak ditemukan. Mungkin sudah dipindahkan atau dihapus.",
                )
            }
            return
        }
        entries.forEach { knownPositions[it.fileName.lowercase()] = it.resumeMs }
        _state.update {
            it.copy(loading = false, entries = entries, index = startIndex)
        }
    }

    /**
     * Plays every video file in the same folder, ordered by episode number. The
     * scan also covers files from an earlier install and custom SAF folders, so
     * the playlist matches what Koleksi shows.
     */
    private suspend fun loadFolderEntries(): List<PlayerEntry> = withContext(Dispatchers.IO) {
        val folder = runCatching { storage.scanCollections() }
            .getOrNull()
            ?.firstOrNull { it.name.equals(folderName, true) }
        val videos = folder?.files
            ?.filter { it.isVideo }
            ?.sortedWith(compareBy({ episodeNumber(it.name) }, { it.name.lowercase() }))
            .orEmpty()
        if (videos.isEmpty()) {
            // The folder scan can miss a file whose provider is momentarily
            // unavailable; the single-file lookup is the last resort.
            val uri = runCatching { storage.findDownloadUri(folderName, fileName) }.getOrNull()
                ?: return@withContext emptyList()
            return@withContext listOf(
                PlayerEntry(
                    fileName = fileName,
                    title = episodeTitle(fileName),
                    uri = uri,
                    size = resolveSize(uri),
                    resumeMs = resumeFor(fileName),
                ),
            )
        }
        videos.mapNotNull { file ->
            val uri = file.uri
                ?: runCatching { storage.findDownloadUri(folderName, file.name) }.getOrNull()
                ?: return@mapNotNull null
            PlayerEntry(
                fileName = file.name,
                title = episodeTitle(file.name),
                uri = uri,
                size = file.size,
                resumeMs = resumeFor(file.name),
            )
        }
            // Location scans are merged by name and size, so the same episode
            // stored in two places could otherwise appear twice in the playlist.
            .distinctBy { it.fileName.lowercase() }
    }

    /**
     * Switches to another episode, saving where the current one stopped first.
     * [positionMs] comes from the player screen because only it knows the live
     * position.
     */
    fun playIndex(index: Int, positionMs: Long) {
        val current = _state.value
        val entry = current.current ?: return
        if (index == current.index || index !in current.entries.indices) return
        if (positionMs > 0L && !completed) {
            positions.saveInBackground(folderName, entry.fileName, positionMs)
            knownPositions[entry.fileName.lowercase()] = positionMs
        }
        completed = false
        lastSavedPosition = 0L
        _state.update { current ->
            val target = current.entries[index]
            val entries = current.entries.map { item ->
                when (item.fileName) {
                    entry.fileName -> item.copy(resumeMs = positionMs.coerceAtLeast(0L))
                    target.fileName -> item.copy(
                        resumeMs = knownPositions[item.fileName.lowercase()] ?: item.resumeMs,
                    )
                    else -> item
                }
            }
            current.copy(entries = entries, index = index)
        }
    }

    /** Called by the player every few seconds while playing. */
    fun onPosition(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        val entry = _state.value.current ?: return
        knownPositions[entry.fileName.lowercase()] = positionMs
        if (abs(positionMs - lastSavedPosition) >= POSITION_SAVE_INTERVAL_MS) {
            lastSavedPosition = positionMs
            positions.saveInBackground(folderName, entry.fileName, positionMs)
        }
        if (!completed && durationMs > 0L && positionMs >= durationMs * WATCHED_RATIO) {
            markCompleted()
        }
    }

    /** Playback reached the end of the file. */
    fun onPlaybackEnded() {
        markCompleted()
    }

    /** Saves the current position when the app stops or the screen closes. */
    fun onStop(positionMs: Long) {
        if (positionMs <= 0L || completed) return
        val entry = _state.value.current ?: return
        knownPositions[entry.fileName.lowercase()] = positionMs
        positions.saveInBackground(folderName, entry.fileName, positionMs)
    }

    private fun markCompleted() {
        if (completed) return
        val entry = _state.value.current ?: return
        completed = true
        knownPositions[entry.fileName.lowercase()] = 0L
        positions.clearInBackground(folderName, entry.fileName)
        // App-lifetime write: the user can leave the screen the moment the video
        // ends, so this must not depend on viewModelScope staying alive.
        smartStorage.markWatchedInBackground(folderName, entry.fileName, entry.size)
        Timber.d("Smart Storage: %s selesai diputar di pemutar internal", entry.fileName)
    }

    private suspend fun resumeFor(fileName: String): Long =
        runCatching { positions.positionFor(folderName, fileName) }.getOrDefault(0L)

    private fun resolveSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 5_000L
        const val WATCHED_RATIO = 0.9

        /** Downloads are named `<episode-slug>_<format>_<resolution>.ext`. */
        val EPISODE_PATTERN = Regex("(?i)(?:episode|eps?)[-_ ]?(\\d{1,4})")

        fun episodeNumber(fileName: String): Int =
            EPISODE_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

        fun episodeTitle(fileName: String): String {
            val base = fileName.substringBeforeLast('.')
            val parts = base.split('_')
            val withoutFormat = if (parts.size >= 3) parts.dropLast(2).joinToString("_") else base
            return prettyName(withoutFormat.ifBlank { base })
        }
    }
}
