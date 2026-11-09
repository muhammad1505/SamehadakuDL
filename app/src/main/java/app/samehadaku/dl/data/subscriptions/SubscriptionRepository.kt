package app.samehadaku.dl.data.subscriptions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.Subscription
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class ImportSummary(
    val imported: Int,
    val updated: Int,
    val skipped: Int,
)

@Singleton
class SubscriptionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val subscriptions: Flow<List<Subscription>> = dataStore.data.map { preferences ->
        parseSubscriptions(preferences[KEY_SUBSCRIPTIONS])
    }

    suspend fun currentSubscriptions(): List<Subscription> = subscriptions.first()

    /** Portable backup compatible with the script importer. */
    suspend fun exportForBackup(): String {
        val items = currentSubscriptions()
        val progress = JSONObject()
        items.forEach { subscription ->
            progress.put(
                subscription.name,
                JSONObject().apply {
                    put("last", subscription.lastEpisode)
                    put("url", subscription.animeUrl)
                },
            )
        }
        return JSONObject().apply {
            put("version", 1)
            put("exported_at", System.currentTimeMillis())
            put("subs", JSONArray(items.map { it.name }))
            put("progress", progress)
        }.toString(2)
    }

    /** Adds a new subscription or merges it with an existing one (by slug). */
    suspend fun addOrUpdate(subscription: Subscription): Boolean {
        var created = false
        dataStore.edit { preferences ->
            val current = parseSubscriptions(preferences[KEY_SUBSCRIPTIONS]).toMutableList()
            val index = current.indexOfFirst { it.slug.equals(subscription.slug, ignoreCase = true) }
            if (index >= 0) {
                val existing = current[index]
                current[index] = existing.copy(
                    name = subscription.name.ifBlank { existing.name },
                    animeUrl = subscription.animeUrl.ifBlank { existing.animeUrl },
                    lastEpisode = maxOf(existing.lastEpisode, subscription.lastEpisode),
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                current.add(subscription)
                created = true
            }
            preferences[KEY_SUBSCRIPTIONS] = serialize(current)
        }
        return created
    }

    suspend fun remove(slug: String) {
        dataStore.edit { preferences ->
            val current = parseSubscriptions(preferences[KEY_SUBSCRIPTIONS])
            val filtered = current.filterNot { it.slug.equals(slug, ignoreCase = true) }
            if (filtered.size != current.size) {
                preferences[KEY_SUBSCRIPTIONS] = serialize(filtered)
            }
        }
    }

    /** Manual correction, like "menu subscribe > atur episode terakhir" in the script. */
    suspend fun setLastEpisode(slug: String, episodeNumber: Int) {
        dataStore.edit { preferences ->
            val current = parseSubscriptions(preferences[KEY_SUBSCRIPTIONS]).toMutableList()
            val index = current.indexOfFirst { it.slug.equals(slug, ignoreCase = true) }
            if (index >= 0) {
                current[index] = current[index].copy(
                    lastEpisode = episodeNumber.coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis(),
                )
                preferences[KEY_SUBSCRIPTIONS] = serialize(current)
            }
        }
    }

    /** Called whenever a download finishes, so "Lanjutkan" never repeats an episode. */
    suspend fun recordDownloadedEpisode(slug: String, episodeNumber: Int) {
        if (episodeNumber <= 0) return
        dataStore.edit { preferences ->
            val current = parseSubscriptions(preferences[KEY_SUBSCRIPTIONS]).toMutableList()
            val index = current.indexOfFirst { it.slug.equals(slug, ignoreCase = true) }
            if (index >= 0 && episodeNumber > current[index].lastEpisode) {
                current[index] = current[index].copy(
                    lastEpisode = episodeNumber,
                    updatedAt = System.currentTimeMillis(),
                )
                preferences[KEY_SUBSCRIPTIONS] = serialize(current)
            }
        }
    }

    /**
     * Imports `.samehadaku_subs.json` written by the Python downloader:
     * `subs` holds anime names, `progress` holds `{last, url}` per name.
     */
    suspend fun importFromScript(json: String): ImportSummary {
        val (parsed, skipped) = parseScriptJson(json)
        if (parsed.isEmpty()) {
            throw IllegalArgumentException(
                "Tidak ada langganan yang bisa diimpor dari file ini.",
            )
        }
        var imported = 0
        var updated = 0
        dataStore.edit { preferences ->
            val current = parseSubscriptions(preferences[KEY_SUBSCRIPTIONS]).toMutableList()
            for (subscription in parsed) {
                val index = current.indexOfFirst {
                    it.slug.equals(subscription.slug, ignoreCase = true)
                }
                if (index >= 0) {
                    val existing = current[index]
                    current[index] = existing.copy(
                        name = subscription.name.ifBlank { existing.name },
                        animeUrl = subscription.animeUrl.ifBlank { existing.animeUrl },
                        lastEpisode = maxOf(existing.lastEpisode, subscription.lastEpisode),
                        updatedAt = System.currentTimeMillis(),
                    )
                    updated++
                } else {
                    current.add(subscription)
                    imported++
                }
            }
            preferences[KEY_SUBSCRIPTIONS] = serialize(current)
        }
        return ImportSummary(imported = imported, updated = updated, skipped = skipped)
    }

    private fun parseScriptJson(json: String): Pair<List<Subscription>, Int> {
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            throw IllegalArgumentException("File bukan JSON langganan yang valid.")
        }
        val subs = root.optJSONArray("subs") ?: throw IllegalArgumentException(
            "File tidak berisi daftar \"subs\". Pilih .samehadaku_subs.json dari script.",
        )
        if (subs.length() == 0) {
            throw IllegalArgumentException("Daftar langganan di file kosong.")
        }
        val home = root.optString("home").trim().trimEnd('/')
        val progress = root.optJSONObject("progress") ?: JSONObject()

        var skipped = 0
        val parsed = mutableListOf<Subscription>()
        for (index in 0 until subs.length()) {
            val name = subs.optString(index).trim()
            if (name.isBlank()) {
                skipped++
                continue
            }
            val entry = progress.optJSONObject(name)
            val last = entry?.optInt("last", 0) ?: 0
            val rawUrl = entry?.optString("url").orEmpty().trim()
            val url = when {
                rawUrl.isNotBlank() -> UrlUtils.normalizeUrl(rawUrl)
                home.isNotBlank() -> "$home/anime/${Names.toSlug(name)}/"
                else -> ""
            }
            if (url.isBlank()) {
                skipped++
                continue
            }
            val slug = UrlUtils.lastPathSegment(url).ifBlank { Names.toSlug(name) }
            if (slug.isBlank()) {
                skipped++
                continue
            }
            parsed.add(
                Subscription(
                    name = name,
                    animeUrl = url,
                    slug = slug,
                    lastEpisode = last.coerceAtLeast(0),
                ),
            )
        }
        return parsed to skipped
    }

    private fun serialize(items: List<Subscription>): String {
        val array = JSONArray()
        items.sortedBy { it.name.lowercase() }.forEach { subscription ->
            array.put(
                JSONObject().apply {
                    put("name", subscription.name)
                    put("url", subscription.animeUrl)
                    put("slug", subscription.slug)
                    put("last", subscription.lastEpisode)
                    put("added", subscription.addedAt)
                    put("updated", subscription.updatedAt)
                },
            )
        }
        return array.toString()
    }

    private fun parseSubscriptions(raw: String?): List<Subscription> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    val slug = item.optString("slug").ifBlank { UrlUtils.lastPathSegment(url) }
                    if (slug.isBlank()) continue
                    add(
                        Subscription(
                            name = item.optString("name").ifBlank { Names.fromSlug(slug) },
                            animeUrl = url,
                            slug = slug,
                            lastEpisode = item.optInt("last", 0).coerceAtLeast(0),
                            addedAt = item.optLong("added", 0L),
                            updatedAt = item.optLong("updated", 0L),
                        ),
                    )
                }
            }.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions_v1")
    }
}
