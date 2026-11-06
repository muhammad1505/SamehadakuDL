package app.samehadaku.dl.data.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.core.UrlUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class CloudflareSnapshot(
    val host: String,
    val userAgent: String,
    val cookies: String,
)

/**
 * Holds the exact WebView User-Agent and cookie header obtained after a successful
 * Cloudflare challenge. cf_clearance is bound to both the UA and IP, so every OkHttp
 * request to the site must reuse this identical pair.
 */
@Singleton
class CloudflareSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    @Volatile
    private var cached: CloudflareSnapshot? = null

    private val _active = MutableStateFlow(false)

    /** Reactive session state so settings/profile UI updates immediately after verify/reset. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun snapshot(): CloudflareSnapshot? = cached

    fun hasSession(): Boolean = cached != null

    suspend fun restore() {
        val preferences = dataStore.data.first()
        val host = preferences[KEY_HOST]
        val userAgent = preferences[KEY_USER_AGENT]
        val cookies = preferences[KEY_COOKIES]
        cached = if (!host.isNullOrBlank() && !userAgent.isNullOrBlank() && !cookies.isNullOrBlank()) {
            CloudflareSnapshot(host, userAgent, cookies)
        } else {
            null
        }
        _active.value = cached != null
    }

    suspend fun save(host: String, userAgent: String, cookies: String) {
        if (host.isBlank() || userAgent.isBlank() || cookies.isBlank()) return
        val snapshot = CloudflareSnapshot(host, userAgent, cookies)
        cached = snapshot
        _active.value = true
        dataStore.edit { preferences ->
            preferences[KEY_HOST] = snapshot.host
            preferences[KEY_USER_AGENT] = snapshot.userAgent
            preferences[KEY_COOKIES] = snapshot.cookies
        }
    }

    suspend fun clear() {
        cached = null
        _active.value = false
        dataStore.edit { preferences ->
            preferences.remove(KEY_HOST)
            preferences.remove(KEY_USER_AGENT)
            preferences.remove(KEY_COOKIES)
        }
    }

    fun appliesTo(requestHost: String): Boolean {
        val snapshot = cached ?: return false
        if (requestHost.equals(snapshot.host, ignoreCase = true)) return true
        return UrlUtils.isSamehadakuHost(requestHost) && UrlUtils.isSamehadakuHost(snapshot.host)
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("cloudflare_host")
        val KEY_USER_AGENT = stringPreferencesKey("cloudflare_user_agent")
        val KEY_COOKIES = stringPreferencesKey("cloudflare_cookies")
    }
}
