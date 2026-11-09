package app.samehadaku.dl.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.model.VideoFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val recentUrls: Flow<List<String>> = dataStore.data.map { preferences ->
        parseRecentUrls(preferences[KEY_RECENT_URLS])
    }

    val lastSeenVersion: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_SEEN_VERSION]
    }

    val concurrency: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[KEY_CONCURRENCY] ?: 1).coerceIn(1, 3)
    }

    /**
     * Lets a download use the closest available quality when the exact saved
     * format/resolution is not uploaded yet. On by default so followed anime keep
     * downloading even when the uploader changes format between episodes.
     */
    val qualityDowngrade: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_QUALITY_DOWNGRADE] ?: true
    }

    val downloadTreeUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_DOWNLOAD_TREE_URI]
    }

    /** Last quality the user downloaded with; reused on the next download/continue. */
    val qualityPreference: Flow<QualityPreference?> = dataStore.data.map { preferences ->
        parseQualityPreference(preferences[KEY_QUALITY_PREFERENCE])
    }

    /** Periodic background check of followed anime; on by default. */
    val autoCheckEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CHECK_ENABLED] ?: true
    }

    /**
     * How often followed anime are checked, in minutes. Reads the old hours key for
     * installs that still have a value from before the interval could go below 1 hour.
     */
    val autoCheckIntervalMinutes: Flow<Int> = dataStore.data.map { preferences ->
        val stored = preferences[KEY_AUTO_CHECK_INTERVAL_MINUTES]
            ?: preferences[KEY_AUTO_CHECK_INTERVAL_HOURS]?.times(60)
            ?: DEFAULT_AUTO_CHECK_MINUTES
        stored.coerceIn(MIN_AUTO_CHECK_MINUTES, MAX_AUTO_CHECK_MINUTES)
    }

    val lastAutoCheckAt: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_AUTO_CHECK_AT] ?: 0L
    }

    /** Automatic retry of downloads that failed because a host link was dead. */
    val failedRetryEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_FAILED_RETRY_ENABLED] ?: true
    }

    suspend fun addRecentUrl(url: String) {
        val current = recentUrls.first()
        val updated = (listOf(url) + current.filterNot { it.equals(url, ignoreCase = true) })
            .take(MAX_RECENT_URLS)
        val json = JSONArray(updated).toString()
        dataStore.edit { it[KEY_RECENT_URLS] = json }
    }

    suspend fun removeRecentUrl(url: String) {
        val current = recentUrls.first()
        val updated = current.filterNot { it.equals(url, ignoreCase = true) }
        dataStore.edit { it[KEY_RECENT_URLS] = JSONArray(updated).toString() }
    }

    suspend fun clearRecentUrls() {
        dataStore.edit { it.remove(KEY_RECENT_URLS) }
    }

    suspend fun shouldShowChangelog(version: String): Boolean =
        lastSeenVersion.first() != version

    suspend fun markChangelogSeen(version: String) {
        dataStore.edit { it[KEY_LAST_SEEN_VERSION] = version }
    }

    suspend fun setConcurrency(value: Int) {
        dataStore.edit { it[KEY_CONCURRENCY] = value.coerceIn(1, 3) }
    }

    suspend fun setQualityDowngrade(enabled: Boolean) {
        dataStore.edit { it[KEY_QUALITY_DOWNGRADE] = enabled }
    }

    suspend fun setDownloadTreeUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(KEY_DOWNLOAD_TREE_URI)
            } else {
                preferences[KEY_DOWNLOAD_TREE_URI] = uri
            }
        }
    }

    suspend fun currentConcurrency(): Int = concurrency.first()

    suspend fun currentQualityDowngrade(): Boolean = qualityDowngrade.first()

    suspend fun currentDownloadTreeUri(): String? = downloadTreeUri.first()

    suspend fun saveQualityPreference(format: VideoFormat, resolution: String, host: Host) {
        val json = JSONObject().apply {
            put("format", format.label)
            put("resolution", resolution)
            put("host", host.name)
        }.toString()
        dataStore.edit { it[KEY_QUALITY_PREFERENCE] = json }
    }

    suspend fun currentQualityPreference(): QualityPreference? = qualityPreference.first()

    suspend fun clearQualityPreference() {
        dataStore.edit { it.remove(KEY_QUALITY_PREFERENCE) }
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CHECK_ENABLED] = enabled }
    }

    suspend fun setAutoCheckIntervalMinutes(minutes: Int) {
        dataStore.edit {
            it[KEY_AUTO_CHECK_INTERVAL_MINUTES] =
                minutes.coerceIn(MIN_AUTO_CHECK_MINUTES, MAX_AUTO_CHECK_MINUTES)
        }
    }

    suspend fun setLastAutoCheckAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_AUTO_CHECK_AT] = epochMillis }
    }

    suspend fun setFailedRetryEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FAILED_RETRY_ENABLED] = enabled }
    }

    suspend fun currentFailedRetryEnabled(): Boolean = failedRetryEnabled.first()

    suspend fun currentAutoCheckEnabled(): Boolean = autoCheckEnabled.first()

    suspend fun currentAutoCheckIntervalMinutes(): Int = autoCheckIntervalMinutes.first()

    suspend fun currentLastAutoCheckAt(): Long = lastAutoCheckAt.first()

    private fun parseQualityPreference(raw: String?): QualityPreference? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            val format = VideoFormat.fromLabel(json.optString("format")) ?: return null
            val resolution = json.optString("resolution").trim().ifBlank { return null }
            val host = Host.entries.firstOrNull { it.name == json.optString("host") } ?: return null
            QualityPreference(format = format, resolution = resolution, host = host)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRecentUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val MAX_RECENT_URLS = 10
        const val DEFAULT_AUTO_CHECK_MINUTES = 60

        /** WorkManager refuses periodic work shorter than 15 minutes. */
        const val MIN_AUTO_CHECK_MINUTES = 15
        const val MAX_AUTO_CHECK_MINUTES = 24 * 60
        val KEY_RECENT_URLS = stringPreferencesKey("recent_urls")
        val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val KEY_CONCURRENCY = intPreferencesKey("concurrency")
        val KEY_QUALITY_DOWNGRADE = booleanPreferencesKey("quality_downgrade")
        val KEY_DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
        val KEY_QUALITY_PREFERENCE = stringPreferencesKey("quality_preference")
        val KEY_AUTO_CHECK_ENABLED = booleanPreferencesKey("auto_check_enabled")
        val KEY_AUTO_CHECK_INTERVAL_MINUTES = intPreferencesKey("auto_check_interval_minutes")
        val KEY_AUTO_CHECK_INTERVAL_HOURS = intPreferencesKey("auto_check_interval_hours")
        val KEY_LAST_AUTO_CHECK_AT = longPreferencesKey("last_auto_check_at")
        val KEY_FAILED_RETRY_ENABLED = booleanPreferencesKey("failed_retry_enabled")
    }
}
