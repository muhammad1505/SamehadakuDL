package app.samehadaku.dl.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class QueueStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun save(tasks: List<DownloadTask>) {
        runCatching {
            val array = JSONArray()
            for (task in tasks) {
                array.put(
                    JSONObject().apply {
                        put("id", task.id)
                        put("episodeUrl", task.episodeUrl)
                        put("episodeNumber", task.episodeNumber)
                        put("animeSlug", task.animeSlug)
                        put("format", task.format.label)
                        put("resolution", task.resolution)
                        put("preferredHost", task.preferredHost.label)
                        put("state", task.state.name)
                        put("bytesDone", task.bytesDone)
                        put("totalBytes", task.totalBytes)
                        put("currentHost", task.currentHost?.label)
                        put("fileName", task.fileName)
                        put("note", task.note)
                        put("alreadyExisted", task.alreadyExisted)
                        put("retryCount", task.retryCount)
                        put("createdAt", task.createdAt)
                        if (task.finishedAt != null) {
                            put("finishedAt", task.finishedAt)
                        }
                        put("failureReasons", JSONArray(task.failureReasons))
                    },
                )
            }
            dataStore.edit { preferences ->
                preferences[KEY_QUEUE] = array.toString()
            }
        }
    }

    suspend fun load(): List<DownloadTask> {
        val raw = runCatching { dataStore.data.first()[KEY_QUEUE] }.getOrNull()
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val task = parseTask(item) ?: continue
                    add(task)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseTask(item: JSONObject): DownloadTask? {
        val id = item.optString("id")
        val episodeUrl = item.optString("episodeUrl")
        if (id.isBlank() || episodeUrl.isBlank()) return null
        val format = VideoFormat.fromLabel(item.optString("format")) ?: return null
        val host = parseHost(item.optString("preferredHost")) ?: Host.PIXELDRAIN
        val state = runCatching {
            DownloadState.valueOf(item.optString("state"))
        }.getOrDefault(DownloadState.PAUSED)
        val reasons = buildList {
            val array = item.optJSONArray("failureReasons") ?: JSONArray()
            for (index in 0 until array.length()) {
                val reason = array.optString(index)
                if (reason.isNotBlank()) add(reason)
            }
        }
        val createdAt = item.optLong("createdAt", 0L)
        val finishedAt = if (item.has("finishedAt") && !item.isNull("finishedAt")) {
            item.optLong("finishedAt")
        } else {
            null
        }

        return DownloadTask(
            id = id,
            episodeUrl = episodeUrl,
            episodeNumber = item.optInt("episodeNumber", 1),
            animeSlug = item.optString("animeSlug"),
            format = format,
            resolution = item.optString("resolution"),
            preferredHost = host,
            state = state,
            bytesDone = item.optLong("bytesDone", 0L),
            totalBytes = item.optLong("totalBytes", 0L),
            currentHost = parseHost(item.optString("currentHost")),
            failureReasons = reasons,
            note = if (item.isNull("note")) null else item.optString("note").ifBlank { null },
            fileName = if (item.isNull("fileName")) null else item.optString("fileName").ifBlank { null },
            alreadyExisted = item.optBoolean("alreadyExisted", false),
            retryCount = item.optInt("retryCount", 0),
            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
            finishedAt = finishedAt,
        )
    }

    /** Saved tasks use the enum name; fall back to URL/label matching for old rows. */
    private fun parseHost(raw: String): Host? =
        Host.entries.firstOrNull { it.name == raw } ?: Host.fromLink(raw)

    private companion object {
        val KEY_QUEUE = stringPreferencesKey("download_queue")
    }
}
