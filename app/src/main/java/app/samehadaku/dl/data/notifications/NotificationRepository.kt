package app.samehadaku.dl.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.samehadaku.dl.domain.model.AppNotification
import app.samehadaku.dl.domain.model.AppNotificationKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the in-app notification inbox. System notifications are ephemeral (they can be
 * dismissed or replaced), so every one is mirrored here and the Beranda bell shows a
 * badge until it is read.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val notifications: Flow<List<AppNotification>> = dataStore.data.map { preferences ->
        parse(preferences[KEY_NOTIFICATIONS])
    }

    val unreadCount: Flow<Int> = notifications.map { items -> items.count { !it.read } }

    suspend fun add(
        kind: AppNotificationKind,
        title: String,
        message: String,
        route: String,
    ) {
        val entry = AppNotification(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            message = message,
            route = route,
        )
        dataStore.edit { preferences ->
            val current = parse(preferences[KEY_NOTIFICATIONS])
            preferences[KEY_NOTIFICATIONS] = serialize((listOf(entry) + current).take(MAX_ITEMS))
        }
    }

    suspend fun markRead(id: String) {
        dataStore.edit { preferences ->
            val current = parse(preferences[KEY_NOTIFICATIONS])
            if (current.none { it.id == id && !it.read }) return@edit
            preferences[KEY_NOTIFICATIONS] = serialize(
                current.map { item -> if (item.id == id) item.copy(read = true) else item },
            )
        }
    }

    suspend fun markAllRead() {
        dataStore.edit { preferences ->
            val current = parse(preferences[KEY_NOTIFICATIONS])
            if (current.none { !it.read }) return@edit
            preferences[KEY_NOTIFICATIONS] = serialize(
                current.map { item -> item.copy(read = true) },
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY_NOTIFICATIONS) }
    }

    private fun serialize(items: List<AppNotification>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("kind", item.kind.name)
                    put("title", item.title)
                    put("message", item.message)
                    put("route", item.route)
                    put("created", item.createdAt)
                    put("read", item.read)
                },
            )
        }
        return array.toString()
    }

    private fun parse(raw: String?): List<AppNotification> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title")
                    if (title.isBlank()) continue
                    add(
                        AppNotification(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            kind = runCatching {
                                AppNotificationKind.valueOf(item.optString("kind"))
                            }.getOrDefault(AppNotificationKind.INFO),
                            title = title,
                            message = item.optString("message"),
                            route = item.optString("route"),
                            createdAt = item.optLong("created", 0L),
                            read = item.optBoolean("read", false),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val MAX_ITEMS = 50
        val KEY_NOTIFICATIONS = stringPreferencesKey("notification_inbox_v1")
    }
}
