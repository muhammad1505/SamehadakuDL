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

data class PlayerUiState(
    val loading: Boolean = true,
    val uri: Uri? = null,
    val title: String = "",
    val folderLabel: String = "",
    val resumeMs: Long = 0L,
    val error: String? = null,
)

/**
 * Internal playback with real progress. Unlike the external-player heuristic,
 * the player itself reports the position, so an episode is marked watched only
 * after 90% of the video was actually played and the position is persisted for
 * "continue watching".
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
        PlayerUiState(
            title = fileName,
            folderLabel = prettyName(folderName),
        ),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var lastSavedPosition = 0L
    private var completed = false

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        if (folderName.isBlank() || fileName.isBlank()) {
            _state.update { it.copy(loading = false, error = "Episode tidak dikenal.") }
            return
        }
        val uri = runCatching { storage.findDownloadUri(folderName, fileName) }.getOrNull()
        if (uri == null) {
            _state.update {
                it.copy(
                    loading = false,
                    error = "File tidak ditemukan. Mungkin sudah dipindahkan atau dihapus.",
                )
            }
            return
        }
        val resume = runCatching { positions.positionFor(folderName, fileName) }
            .getOrDefault(0L)
        _state.update {
            it.copy(
                loading = false,
                uri = uri,
                resumeMs = resume,
            )
        }
    }

    /** Called by the player every few seconds while playing. */
    fun onPosition(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        if (abs(positionMs - lastSavedPosition) >= POSITION_SAVE_INTERVAL_MS) {
            lastSavedPosition = positionMs
            positions.saveInBackground(folderName, fileName, positionMs)
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
        positions.saveInBackground(folderName, fileName, positionMs)
    }

    private fun markCompleted() {
        if (completed) return
        completed = true
        positions.clearInBackground(folderName, fileName)
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { resolveSize() }
            smartStorage.markWatchedInBackground(folderName, fileName, size)
            Timber.d("Smart Storage: %s selesai diputar di pemutar internal", fileName)
        }
    }

    private fun resolveSize(): Long {
        val uri = _state.value.uri ?: return 0L
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
    }
}
