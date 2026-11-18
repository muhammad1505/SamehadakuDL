package app.samehadaku.dl.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.core.SmartStoragePolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers where the internal player stopped for each downloaded episode.
 * Writes are small and happen every few seconds, so the store is separate from
 * Smart Storage settings and capped to avoid unbounded growth.
 */
@Singleton
class PlaybackPositionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** App-lifetime scope: a position must survive the player screen closing. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun positionFor(folderName: String, fileName: String): Long {
        val key = SmartStoragePolicy.key(folderName, fileName)
        val raw = runCatching { dataStore.data.first()[KEY_POSITIONS] }.getOrNull()
        return parse(raw).firstOrNull { it.key == key }?.positionMs ?: 0L
    }

    suspend fun save(folderName: String, fileName: String, positionMs: Long) {
        if (folderName.isBlank() || fileName.isBlank()) return
        val key = SmartStoragePolicy.key(folderName, fileName)
        val normalized = SmartStoragePolicy.normalizeFolder(folderName)
        dataStore.edit { preferences ->
            val current = parse(preferences[KEY_POSITIONS])
            val updated = (
                current.filterNot { it.key == key } +
                    Position(normalized, fileName, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
                )
                .sortedByDescending { it.updatedAt }
                .take(MAX_ENTRIES)
            preferences[KEY_POSITIONS] = toJson(updated)
        }
    }

    suspend fun clear(folderName: String, fileName: String) {
        val key = SmartStoragePolicy.key(folderName, fileName)
        dataStore.edit { preferences ->
            val current = parse(preferences[KEY_POSITIONS])
            val updated = current.filterNot { it.key == key }
            if (updated.size == current.size) return@edit
            if (updated.isEmpty()) {
                preferences.remove(KEY_POSITIONS)
            } else {
                preferences[KEY_POSITIONS] = toJson(updated)
            }
        }
    }

    /** Fire-and-forget variant used when the player is closing or the app stops. */
    fun saveInBackground(folderName: String, fileName: String, positionMs: Long) {
        writeScope.launch { runCatching { save(folderName, fileName, positionMs) } }
    }

    fun clearInBackground(folderName: String, fileName: String) {
        writeScope.launch { runCatching { clear(folderName, fileName) } }
    }

    private fun parse(raw: String?): List<Position> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val folder = item.optString("folder").trim()
                    val file = item.optString("file").trim()
                    if (folder.isBlank() || file.isBlank()) continue
                    add(
                        Position(
                            folderName = SmartStoragePolicy.normalizeFolder(folder),
                            fileName = file,
                            positionMs = item.optLong("positionMs", 0L).coerceAtLeast(0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun toJson(entries: List<Position>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("folder", entry.folderName)
                    put("file", entry.fileName)
                    put("positionMs", entry.positionMs)
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        return array.toString()
    }

    private data class Position(
        val folderName: String,
        val fileName: String,
        val positionMs: Long,
        val updatedAt: Long,
    ) {
        val key: String get() = SmartStoragePolicy.key(folderName, fileName)
    }

    private companion object {
        const val MAX_ENTRIES = 300
        val KEY_POSITIONS = stringPreferencesKey("playback_positions")
    }
}
