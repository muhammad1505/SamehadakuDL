package app.samehadaku.dl.data.smartstorage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import app.samehadaku.dl.core.EpisodeFiles
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.data.download.LegacyDownloadCheck
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
        if (!EpisodeFiles.isVideo(fileName)) {
            Timber.w(
                "Smart Storage menolak %s/%s: bukan file video",
                SmartStoragePolicy.normalizeFolder(folderName),
                fileName,
            )
            return false
        }
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
        if (!SmartStoragePolicy.hasUsableSize(size)) {
            Timber.w(
                "Smart Storage menandai %s/%s tanpa ukuran file; " +
                    "file tidak akan dihapus otomatis",
                normalizedFolder,
                fileName,
            )
        }
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
        val normalizedFolder = SmartStoragePolicy.normalizeFolder(folderName)
        if (!EpisodeFiles.isVideo(fileName)) {
            Timber.w(
                "Smart Storage tidak melacak %s/%s: bukan file video",
                normalizedFolder,
                fileName,
            )
            return
        }
        val settings = runCatching { repository.currentSettings() }.getOrNull()
        if (settings == null) {
            Timber.w("Smart Storage tidak dapat membaca pengaturan; pemutaran tidak dicatat")
            return
        }
        if (!settings.enabled) {
            Timber.d("Smart Storage nonaktif; pemutaran %s tidak dicatat", fileName)
            return
        }
        if (isWatched(normalizedFolder, fileName)) {
            Timber.d("Smart Storage melewati %s: sudah ditandai tonton", fileName)
            return
        }

        val size = withContext(Dispatchers.IO) { resolveSize(uri) }
        if (size <= 0L) {
            Timber.w(
                "Smart Storage tidak melacak %s/%s: ukuran file tidak diketahui",
                normalizedFolder,
                fileName,
            )
            return
        }
        if (!isAppDownloaded(normalizedFolder, fileName, size)) return
        val duration = withContext(Dispatchers.IO) { resolveDuration(uri) }
        if (duration <= 0L) {
            Timber.w(
                "Smart Storage tidak melacak %s/%s: durasi video tidak terbaca",
                normalizedFolder,
                fileName,
            )
            return
        }

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
     * Proves the file came from this app's downloader.
     *
     * New files are registered by [StorageWriter.save]. Files downloaded before the
     * registry existed (or after app data was reset) are recognized through the
     * completed queue, then through [StorageWriter.recognizeLegacyDownload], which
     * verifies location, generated name pattern and byte size. Anything that fails
     * is logged with the reason so Koleksi rejections are visible in Log aplikasi.
     */
    private suspend fun isAppDownloaded(
        folderName: String,
        fileName: String,
        size: Long,
    ): Boolean {
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        if (repository.isKnownDownload(normalized, fileName, size)) {
            Timber.d(
                "Smart Storage menerima %s/%s: tercatat sebagai unduhan aplikasi " +
                    "(ukuran %d)",
                normalized,
                fileName,
                size,
            )
            return true
        }

        val queueMatch = runCatching { queueStore.load() }
            .onFailure {
                Timber.w(it, "Smart Storage gagal membaca riwayat unduhan untuk %s", fileName)
            }
            .getOrDefault(emptyList())
            .any { task ->
                task.state == DownloadState.DONE &&
                    task.fileName == fileName &&
                    SmartStoragePolicy.normalizeFolder(task.animeSlug) == normalized
            }
        if (queueMatch) {
            adoptLegacyDownload(
                folderName = normalized,
                fileName = fileName,
                size = size,
                source = "riwayat unduhan selesai",
            )
            return true
        }

        val check = runCatching {
            storage.recognizeLegacyDownload(normalized, fileName, size)
        }.getOrElse { error ->
            Timber.w(
                error,
                "Smart Storage gagal memeriksa asal file %s/%s",
                normalized,
                fileName,
            )
            LegacyDownloadCheck.Rejected(
                "pemeriksaan lokasi file gagal: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }

        return when (check) {
            is LegacyDownloadCheck.Accepted -> {
                adoptLegacyDownload(
                    folderName = normalized,
                    fileName = fileName,
                    size = size,
                    source = check.location,
                )
                true
            }

            is LegacyDownloadCheck.Rejected -> {
                Timber.w(
                    "Smart Storage menolak %s/%s (ukuran %d): %s",
                    normalized,
                    fileName,
                    size,
                    check.reason,
                )
                false
            }
        }
    }

    /** Persists a recognized legacy file so later checks no longer depend on history. */
    private suspend fun adoptLegacyDownload(
        folderName: String,
        fileName: String,
        size: Long,
        source: String,
    ) {
        runCatching {
            repository.rememberDownloadedFile(folderName, fileName, size)
        }.onFailure {
            Timber.w(it, "Smart Storage gagal mencatat unduhan lama %s/%s", folderName, fileName)
        }
        Timber.i(
            "Smart Storage menerima %s/%s: %s (ukuran %d)",
            folderName,
            fileName,
            source,
            size,
        )
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
