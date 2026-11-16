package app.samehadaku.dl.data.smartstorage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import app.samehadaku.dl.core.EpisodeFiles
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.data.download.QueueStore
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.PendingPlayback
import app.samehadaku.dl.domain.model.SmartStorageSettings
import app.samehadaku.dl.domain.model.SmartStorageStats
import app.samehadaku.dl.domain.model.WatchedEpisode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates watched markers and playback detection. Phase 1 has no internal
 * player: the app records when an external player was launched and, when it
 * becomes visible again, compares that elapsed time with the real duration.
 */
@Singleton
class SmartStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmartStorageRepository,
    private val storage: StorageWriter,
    private val queueStore: QueueStore,
) {

    val settings: Flow<SmartStorageSettings> = repository.settings
    val watchedEpisodes: Flow<List<WatchedEpisode>> = repository.watchedEpisodes
    val stats: Flow<SmartStorageStats> = repository.stats

    suspend fun setEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
        if (!enabled) repository.clearPending()
    }

    suspend fun setGraceDays(days: Int) {
        repository.setGraceDays(days)
    }

    /** Manual marker from Koleksi; works even while Smart Storage is off. */
    suspend fun markWatched(folderName: String, fileName: String, size: Long): Boolean {
        if (!EpisodeFiles.isVideo(fileName)) return false
        val normalizedFolder = SmartStoragePolicy.normalizeFolder(folderName)
        if (!isAppDownloaded(normalizedFolder, fileName, size)) return false
        repository.addWatched(
            WatchedEpisode(
                folderName = normalizedFolder,
                fileName = fileName,
                size = size.coerceAtLeast(0L),
                watchedAt = System.currentTimeMillis(),
                manual = true,
            ),
        )
        return true
    }

    suspend fun unmarkWatched(folderName: String, fileName: String) {
        repository.removeWatched(folderName, fileName)
    }

    suspend fun clearForFile(folderName: String, fileName: String) {
        repository.removeWatched(folderName, fileName)
        repository.removePending(folderName, fileName)
        repository.forgetDownloadedFile(folderName, fileName)
    }

    suspend fun clearForFolder(folderName: String) {
        repository.removeWatchedForFolder(folderName)
        repository.forgetDownloadedFolder(folderName)
        val pending = repository.currentPending().filter { entry ->
            SmartStoragePolicy.normalizeFolder(entry.folderName) ==
                SmartStoragePolicy.normalizeFolder(folderName)
        }
        pending.forEach { entry ->
            repository.removePending(entry.folderName, entry.fileName)
        }
    }

    /**
     * Called after a video URI was handed to an external player. The start time
     * is kept across repeated opens while the elapsed time is still inside the
     * accepted window, so watching in two sittings can still count.
     */
    suspend fun recordPlaybackStarted(folderName: String, fileName: String, uri: Uri) {
        if (!EpisodeFiles.isVideo(fileName)) return
        val settings = runCatching { repository.currentSettings() }.getOrNull() ?: return
        if (!settings.enabled) return
        if (isWatched(folderName, fileName)) return

        val size = withContext(Dispatchers.IO) { resolveSize(uri) }
        if (size <= 0L) return
        val normalizedFolder = SmartStoragePolicy.normalizeFolder(folderName)
        if (!isAppDownloaded(normalizedFolder, fileName, size)) return
        val duration = withContext(Dispatchers.IO) { resolveDuration(uri) }
        if (duration <= 0L) return

        val now = System.currentTimeMillis()
        val existing = repository.currentPending().firstOrNull { entry ->
            SmartStoragePolicy.key(entry.folderName, entry.fileName) ==
                SmartStoragePolicy.key(normalizedFolder, fileName)
        }
        val maxElapsed = (duration * SmartStoragePolicy.maxWatchRatio).toLong()
        val keepStart = existing != null &&
            existing.size == size &&
            now - existing.startedAt in 0..maxElapsed

        repository.upsertPending(
            PendingPlayback(
                folderName = normalizedFolder,
                fileName = fileName,
                size = size,
                startedAt = if (keepStart && existing != null) existing.startedAt else now,
                durationMs = duration,
            ),
        )
    }

    /**
     * Called when the app becomes visible again. A pending playback that lasted
     * roughly 80%–300% of the video duration becomes a watched marker. Stale or
     * missing files are dropped without deleting anything.
     */
    suspend fun evaluatePendingPlayback(now: Long = System.currentTimeMillis()): Int {
        val settings = runCatching { repository.currentSettings() }.getOrNull()
        if (settings?.enabled != true) {
            repository.clearPending()
            return 0
        }

        val watchedKeys = repository.currentWatched()
            .map { entry -> SmartStoragePolicy.key(entry.folderName, entry.fileName) }
            .toSet()
        var marked = 0

        for (pending in repository.currentPending()) {
            val key = SmartStoragePolicy.key(pending.folderName, pending.fileName)
            if (key in watchedKeys || !SmartStoragePolicy.hasUsableSize(pending.size)) {
                repository.removePending(pending.folderName, pending.fileName)
                continue
            }
            val elapsed = now - pending.startedAt
            when {
                elapsed < 0L -> repository.removePending(pending.folderName, pending.fileName)

                SmartStoragePolicy.looksWatched(
                    startedAt = pending.startedAt,
                    finishedAt = now,
                    durationMs = pending.durationMs,
                ) -> {
                    val stillThere = runCatching {
                        storage.existsWithSize(
                            pending.folderName,
                            pending.fileName,
                            pending.size,
                        )
                    }.getOrDefault(false)
                    if (stillThere) {
                        repository.addWatched(
                            WatchedEpisode(
                                folderName = pending.folderName,
                                fileName = pending.fileName,
                                size = pending.size,
                                watchedAt = now,
                                manual = false,
                            ),
                        )
                        marked++
                    }
                    repository.removePending(pending.folderName, pending.fileName)
                }

                elapsed > (pending.durationMs * SmartStoragePolicy.maxWatchRatio).toLong() ->
                    repository.removePending(pending.folderName, pending.fileName)
            }
        }
        if (marked > 0) Timber.d("Smart Storage marked %d episode(s) watched", marked)
        return marked
    }

    private suspend fun isWatched(folderName: String, fileName: String): Boolean {
        val key = SmartStoragePolicy.key(folderName, fileName)
        return repository.currentWatched().any { entry ->
            SmartStoragePolicy.key(entry.folderName, entry.fileName) == key
        }
    }

    /**
     * Proves the file came from this app's downloader. New files are registered
     * by [StorageWriter.save]; files downloaded before the registry existed are
     * matched against completed queue rows while that history is still present.
     * Anything else (user-copied videos, arbitrary folders) is ignored.
     */
    private suspend fun isAppDownloaded(
        folderName: String,
        fileName: String,
        size: Long,
    ): Boolean {
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        if (repository.isKnownDownload(normalized, fileName, size)) return true
        val tasks = runCatching { queueStore.load() }.getOrDefault(emptyList())
        return tasks.any { task ->
            task.state == DownloadState.DONE &&
                task.fileName == fileName &&
                SmartStoragePolicy.normalizeFolder(task.animeSlug) == normalized
        }
    }

    private fun resolveSize(uri: Uri): Long {
        val fromProvider = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
        if (fromProvider > 0L) return fromProvider
        return runCatching {
            DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
        }.getOrDefault(0L)
    }

    private fun resolveDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        } catch (error: Exception) {
            Timber.d(error, "Smart Storage cannot read video duration")
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
