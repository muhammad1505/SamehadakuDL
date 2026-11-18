package app.samehadaku.dl.data.smartstorage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.domain.model.PendingPlayback
import app.samehadaku.dl.domain.model.SmartStorageSettings
import app.samehadaku.dl.domain.model.SmartStorageStats
import app.samehadaku.dl.domain.model.WatchedEpisode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists Smart Storage configuration, watched markers and playback starts.
 * Only markers for files the app itself downloaded are stored; the cleaner
 * verifies each file's name and byte length again before deleting anything.
 */
@Singleton
class SmartStorageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<SmartStorageSettings> = dataStore.data.map { preferences ->
        SmartStorageSettings(
            enabled = preferences[KEY_ENABLED] ?: false,
            graceDays = SmartStoragePolicy.normalizeGraceDays(
                preferences[KEY_GRACE_DAYS] ?: SmartStoragePolicy.defaultGraceDays,
            ),
        )
    }

    val watchedEpisodes: Flow<List<WatchedEpisode>> = dataStore.data.map { preferences ->
        parseWatched(preferences[KEY_WATCHED])
    }

    val stats: Flow<SmartStorageStats> = dataStore.data.map { preferences ->
        val watched = parseWatched(preferences[KEY_WATCHED])
        SmartStorageStats(
            watchedCount = watched.size,
            watchedBytes = watched.sumOf { it.size },
            deletedCount = preferences[KEY_DELETED_COUNT] ?: 0,
            reclaimedBytes = preferences[KEY_RECLAIMED_BYTES] ?: 0L,
            lastCleanupAt = preferences[KEY_LAST_CLEANUP_AT] ?: 0L,
            lastCleanupDeleted = preferences[KEY_LAST_CLEANUP_DELETED] ?: 0,
        )
    }

    suspend fun currentSettings(): SmartStorageSettings = settings.first()

    suspend fun currentWatched(): List<WatchedEpisode> = watchedEpisodes.first()

    suspend fun currentPending(): List<PendingPlayback> {
        val raw = runCatching { dataStore.data.first()[KEY_PENDING] }.getOrNull()
        return parsePending(raw)
    }

    suspend fun currentLastCleanupAt(): Long =
        runCatching { dataStore.data.first()[KEY_LAST_CLEANUP_AT] }.getOrNull() ?: 0L

    /** Records a file the download writer just stored, so Smart Storage can trust it. */
    suspend fun rememberDownloadedFile(folderName: String, fileName: String, size: Long) {
        if (folderName.isBlank() || fileName.isBlank() || size <= 0L) return
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        dataStore.edit { preferences ->
            val current = parseKnownDownloads(preferences[KEY_KNOWN_DOWNLOADS])
            val updated = (
                current.filterNot { known ->
                    SmartStoragePolicy.key(known.folderName, known.fileName) ==
                        SmartStoragePolicy.key(normalized, fileName)
                } + KnownDownload(normalized, fileName, size, System.currentTimeMillis())
                )
                .sortedByDescending { it.recordedAt }
                .take(MAX_KNOWN_DOWNLOADS)
            preferences[KEY_KNOWN_DOWNLOADS] = knownDownloadsToJson(updated)
        }
    }

    /** True when this exact folder/name/size was written by the app's download writer. */
    suspend fun isKnownDownload(folderName: String, fileName: String, size: Long): Boolean {
        val key = SmartStoragePolicy.key(folderName, fileName)
        val raw = runCatching { dataStore.data.first()[KEY_KNOWN_DOWNLOADS] }.getOrNull()
        return parseKnownDownloads(raw).any { known ->
            SmartStoragePolicy.key(known.folderName, known.fileName) == key &&
                (size <= 0L || known.size == size)
        }
    }

    suspend fun forgetDownloadedFile(folderName: String, fileName: String) {
        val key = SmartStoragePolicy.key(folderName, fileName)
        dataStore.edit { preferences ->
            val current = parseKnownDownloads(preferences[KEY_KNOWN_DOWNLOADS])
            val updated = current.filterNot { known ->
                SmartStoragePolicy.key(known.folderName, known.fileName) == key
            }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_KNOWN_DOWNLOADS)
            } else {
                preferences[KEY_KNOWN_DOWNLOADS] = knownDownloadsToJson(updated)
            }
        }
    }

    suspend fun forgetDownloadedFolder(folderName: String) {
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        dataStore.edit { preferences ->
            val current = parseKnownDownloads(preferences[KEY_KNOWN_DOWNLOADS])
            val updated = current.filterNot { known ->
                SmartStoragePolicy.normalizeFolder(known.folderName) == normalized
            }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_KNOWN_DOWNLOADS)
            } else {
                preferences[KEY_KNOWN_DOWNLOADS] = knownDownloadsToJson(updated)
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setGraceDays(days: Int) {
        dataStore.edit { it[KEY_GRACE_DAYS] = SmartStoragePolicy.normalizeGraceDays(days) }
    }

    /** Adds a marker once. Re-marking an already watched file keeps its original date. */
    suspend fun addWatched(entry: WatchedEpisode) {
        if (entry.folderName.isBlank() || entry.fileName.isBlank()) return
        dataStore.edit { preferences ->
            val current = parseWatched(preferences[KEY_WATCHED])
            if (current.any { existing -> sameFile(existing, entry) }) return@edit
            val updated = (current + entry)
                .sortedByDescending { it.watchedAt }
                .take(MAX_WATCHED_ENTRIES)
            preferences[KEY_WATCHED] = watchedToJson(updated)
        }
    }

    suspend fun removeWatched(folderName: String, fileName: String) {
        dataStore.edit { preferences ->
            val current = parseWatched(preferences[KEY_WATCHED])
            val updated = current.filterNot { entry ->
                sameFile(entry.folderName, entry.fileName, folderName, fileName)
            }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_WATCHED)
            } else {
                preferences[KEY_WATCHED] = watchedToJson(updated)
            }
        }
    }

    suspend fun removeWatchedForFolder(folderName: String) {
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        dataStore.edit { preferences ->
            val current = parseWatched(preferences[KEY_WATCHED])
            val updated = current.filterNot { entry ->
                SmartStoragePolicy.normalizeFolder(entry.folderName) == normalized
            }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_WATCHED)
            } else {
                preferences[KEY_WATCHED] = watchedToJson(updated)
            }
        }
    }

    suspend fun upsertPending(record: PendingPlayback) {
        if (record.folderName.isBlank() || record.fileName.isBlank()) return
        dataStore.edit { preferences ->
            val current = parsePending(preferences[KEY_PENDING])
            val updated = (current.filterNot { existing -> sameFile(existing, record) } + record)
                .sortedByDescending { it.startedAt }
                .take(MAX_PENDING_ENTRIES)
            preferences[KEY_PENDING] = pendingToJson(updated)
        }
    }

    /**
     * Keeps exactly one pending playback. Only the episode most recently handed
     * to a player can be playing, so older entries are dropped instead of being
     * evaluated (and possibly marked) together on the next return to the app.
     */
    suspend fun replacePending(record: PendingPlayback) {
        if (record.folderName.isBlank() || record.fileName.isBlank()) return
        dataStore.edit { preferences ->
            preferences[KEY_PENDING] = pendingToJson(listOf(record))
        }
    }

    suspend fun removePending(folderName: String, fileName: String) {
        dataStore.edit { preferences ->
            val current = parsePending(preferences[KEY_PENDING])
            val updated = current.filterNot { entry ->
                sameFile(entry.folderName, entry.fileName, folderName, fileName)
            }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_PENDING)
            } else {
                preferences[KEY_PENDING] = pendingToJson(updated)
            }
        }
    }

    suspend fun clearPending() {
        dataStore.edit { it.remove(KEY_PENDING) }
    }

    /**
     * Stores the result of one cleanup pass. Totals are cumulative; the last
     * pass is kept separately so Settings can show when the app last checked.
     */
    suspend fun recordCleanup(deletedFiles: Int, reclaimedBytes: Long, at: Long) {
        dataStore.edit { preferences ->
            if (deletedFiles > 0) {
                preferences[KEY_DELETED_COUNT] =
                    (preferences[KEY_DELETED_COUNT] ?: 0) + deletedFiles
                preferences[KEY_RECLAIMED_BYTES] =
                    (preferences[KEY_RECLAIMED_BYTES] ?: 0L) + reclaimedBytes.coerceAtLeast(0L)
            }
            preferences[KEY_LAST_CLEANUP_AT] = at
            preferences[KEY_LAST_CLEANUP_DELETED] = deletedFiles
        }
    }

    private fun sameFile(first: WatchedEpisode, second: WatchedEpisode): Boolean =
        sameFile(first.folderName, first.fileName, second.folderName, second.fileName)

    private fun sameFile(first: PendingPlayback, second: PendingPlayback): Boolean =
        sameFile(first.folderName, first.fileName, second.folderName, second.fileName)

    private fun sameFile(
        firstFolder: String,
        firstFile: String,
        secondFolder: String,
        secondFile: String,
    ): Boolean = SmartStoragePolicy.key(firstFolder, firstFile) ==
        SmartStoragePolicy.key(secondFolder, secondFile)

    private fun parseWatched(raw: String?): List<WatchedEpisode> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val folder = item.optString("folder").trim()
                    val file = item.optString("file").trim()
                    val watchedAt = item.optLong("watchedAt", 0L)
                    if (folder.isBlank() || file.isBlank() || watchedAt <= 0L) continue
                    add(
                        WatchedEpisode(
                            folderName = SmartStoragePolicy.normalizeFolder(folder),
                            fileName = file,
                            size = item.optLong("size", 0L).coerceAtLeast(0L),
                            watchedAt = watchedAt,
                            manual = item.optBoolean("manual", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun watchedToJson(entries: List<WatchedEpisode>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("folder", entry.folderName)
                    put("file", entry.fileName)
                    put("size", entry.size)
                    put("watchedAt", entry.watchedAt)
                    put("manual", entry.manual)
                },
            )
        }
        return array.toString()
    }

    private fun parsePending(raw: String?): List<PendingPlayback> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val folder = item.optString("folder").trim()
                    val file = item.optString("file").trim()
                    val startedAt = item.optLong("startedAt", 0L)
                    val duration = item.optLong("durationMs", 0L)
                    if (folder.isBlank() || file.isBlank() || startedAt <= 0L || duration <= 0L) {
                        continue
                    }
                    add(
                        PendingPlayback(
                            folderName = SmartStoragePolicy.normalizeFolder(folder),
                            fileName = file,
                            size = item.optLong("size", 0L).coerceAtLeast(0L),
                            startedAt = startedAt,
                            durationMs = duration,
                            watchedMs = item.optLong("watchedMs", 0L).coerceAtLeast(0L),
                            // Entries written before session tracking have no
                            // sessionStartedAt; their old start time is the
                            // sitting that should still be evaluated.
                            sessionStartedAt = item.optLong("sessionStartedAt", startedAt)
                                .coerceAtLeast(0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseKnownDownloads(raw: String?): List<KnownDownload> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val folder = item.optString("folder").trim()
                    val file = item.optString("file").trim()
                    val size = item.optLong("size", 0L)
                    if (folder.isBlank() || file.isBlank() || size <= 0L) continue
                    add(
                        KnownDownload(
                            folderName = SmartStoragePolicy.normalizeFolder(folder),
                            fileName = file,
                            size = size,
                            recordedAt = item.optLong("recordedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun knownDownloadsToJson(entries: List<KnownDownload>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("folder", entry.folderName)
                    put("file", entry.fileName)
                    put("size", entry.size)
                    put("recordedAt", entry.recordedAt)
                },
            )
        }
        return array.toString()
    }

    private data class KnownDownload(
        val folderName: String,
        val fileName: String,
        val size: Long,
        val recordedAt: Long,
    )

    private fun pendingToJson(entries: List<PendingPlayback>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("folder", entry.folderName)
                    put("file", entry.fileName)
                    put("size", entry.size)
                    put("startedAt", entry.startedAt)
                    put("durationMs", entry.durationMs)
                    put("watchedMs", entry.watchedMs)
                    put("sessionStartedAt", entry.sessionStartedAt)
                },
            )
        }
        return array.toString()
    }

    private companion object {
        const val MAX_WATCHED_ENTRIES = 500
        const val MAX_PENDING_ENTRIES = 50
        const val MAX_KNOWN_DOWNLOADS = 2000

        val KEY_ENABLED = booleanPreferencesKey("smart_storage_enabled")
        val KEY_GRACE_DAYS = intPreferencesKey("smart_storage_grace_days")
        val KEY_WATCHED = stringPreferencesKey("smart_storage_watched")
        val KEY_PENDING = stringPreferencesKey("smart_storage_pending")
        val KEY_KNOWN_DOWNLOADS = stringPreferencesKey("smart_storage_known_downloads")
        val KEY_DELETED_COUNT = intPreferencesKey("smart_storage_deleted_count")
        val KEY_RECLAIMED_BYTES = longPreferencesKey("smart_storage_reclaimed_bytes")
        val KEY_LAST_CLEANUP_AT = longPreferencesKey("smart_storage_last_cleanup_at")
        val KEY_LAST_CLEANUP_DELETED = intPreferencesKey("smart_storage_last_cleanup_deleted")
    }
}
