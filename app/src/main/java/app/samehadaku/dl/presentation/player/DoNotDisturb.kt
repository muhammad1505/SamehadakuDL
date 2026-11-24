package app.samehadaku.dl.presentation.player

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import timber.log.Timber

/**
 * Turns the system Do Not Disturb filter on while the user watches a video.
 *
 * Android only allows this through notification-policy access, which the user
 * grants by hand in system settings; the app never asks for it automatically.
 * The filter that was active before is persisted and restored when the player
 * closes or the app goes to the background. If the process dies while DND is
 * active, [restoreIfNeeded] repairs the filter on the next app start instead of
 * leaving the phone silenced.
 *
 * Pausing deliberately keeps the manual "Cinema DND" toggle active; the user can
 * still turn it off from the panel, and leaving the player always restores the
 * previous filter. Recreation for a theme, locale or font-size change is not
 * "leaving": the stored filter is re-read through [isActive] so the toggle keeps
 * showing the state that is actually applied.
 *
 * The filter used is "alarm saja": "prioritas" still lets WhatsApp and other
 * conversation notifications through, which is exactly the interruption the
 * cinema mode is meant to silence. Alarms keep ringing and media is unaffected,
 * so the movie's audio stays audible.
 */
internal class DoNotDisturb(private val context: Context) {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * User intent for the next playback. Leaving the player restores the phone,
     * but this flag makes the filter come back on by itself next time instead of
     * asking for a manual toggle on every episode.
     */
    var wanted: Boolean
        get() = preferences.getBoolean(KEY_WANTED, false)
        set(value) = preferences.edit().putBoolean(KEY_WANTED, value).apply()

    fun hasAccess(): Boolean = manager?.isNotificationPolicyAccessGranted == true

    /**
     * True while the filter stored by [activate] is still applied, so a
     * recomposed screen (or a new process after the activity was recreated)
     * shows the real toggle state instead of a default "off".
     */
    fun isActive(): Boolean {
        if (preferences.getInt(KEY_PREVIOUS_FILTER, NO_FILTER) == NO_FILTER) return false
        val current = runCatching { manager?.currentInterruptionFilter }.getOrNull()
        return current == APPLIED_FILTER
    }

    /** Opens the system screen where the user grants notification-policy access. */
    fun requestAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Pengaturan akses jangan ganggu tidak bisa dibuka") }
    }

    /** Returns true when Do Not Disturb is now active. */
    fun activate(): Boolean {
        val nm = manager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        if (preferences.getInt(KEY_PREVIOUS_FILTER, NO_FILTER) == NO_FILTER) {
            val current = runCatching { nm.currentInterruptionFilter }
                .getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)
            preferences.edit().putInt(KEY_PREVIOUS_FILTER, restorableFilter(current)).apply()
        }
        val activated = runCatching {
            nm.setInterruptionFilter(APPLIED_FILTER)
        }.isSuccess
        if (!activated) preferences.edit().remove(KEY_PREVIOUS_FILTER).apply()
        return activated
    }

    /** Restores the interruption filter that was active before [activate]. */
    fun release() {
        val stored = preferences.getInt(KEY_PREVIOUS_FILTER, NO_FILTER)
        if (stored == NO_FILTER) return
        val nm = manager ?: return
        // If the user changed DND themselves while watching, do not overwrite
        // that choice when the player closes.
        val current = runCatching { nm.currentInterruptionFilter }.getOrNull()
        if (current != null && current != APPLIED_FILTER) {
            preferences.edit().remove(KEY_PREVIOUS_FILTER).apply()
            return
        }
        if (!nm.isNotificationPolicyAccessGranted) return
        val restored = runCatching {
            nm.setInterruptionFilter(restorableFilter(stored))
        }.isSuccess
        if (restored) {
            preferences.edit().remove(KEY_PREVIOUS_FILTER).apply()
        } else {
            // Keep the record so the next app start can retry instead of leaving
            // priority mode behind after a transient binder failure.
            Timber.w("Filter jangan ganggu tidak bisa dipulihkan; akan dicoba lagi")
        }
    }

    companion object {
        private const val PREFERENCES = "player_dnd"
        private const val KEY_PREVIOUS_FILTER = "previous_filter"
        private const val KEY_WANTED = "wanted"
        private const val NO_FILTER = -1

        /**
         * Alarms-only blocks calls and messages (including WhatsApp) while alarms
         * still ring; media playback is never part of the interruption filter.
         */
        private const val APPLIED_FILTER = NotificationManager.INTERRUPTION_FILTER_ALARMS

        /**
         * `INTERRUPTION_FILTER_UNKNOWN` is only reported before the notification
         * policy service is ready and cannot be set back. Storing it would make
         * release a no-op and leave priority mode on after watching, so it is
         * treated as "all interruptions allowed".
         */
        private fun restorableFilter(filter: Int): Int =
            if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                filter
            }

        /**
         * Repairs a filter left behind by a crash or force-stop. The stored value
         * is only applied when DND is still on the priority filter this app set;
         * if the user changed DND themselves in the meantime, their choice wins.
         */
        fun restoreIfNeeded(context: Context) {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val stored = prefs.getInt(KEY_PREVIOUS_FILTER, NO_FILTER)
            if (stored == NO_FILTER) return

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val current = runCatching { manager.currentInterruptionFilter }.getOrNull()
            if (current != null && current != APPLIED_FILTER) {
                // The user changed DND themselves, or the system reset it: the
                // stored filter is stale and must not override that choice.
                prefs.edit().remove(KEY_PREVIOUS_FILTER).apply()
                return
            }
            // Without access the filter cannot be touched; keep the record so a
            // later launch (after access is granted again) can repair it.
            if (!manager.isNotificationPolicyAccessGranted) return
            val restored = runCatching {
                manager.setInterruptionFilter(restorableFilter(stored))
            }.isSuccess
            if (restored) {
                prefs.edit().remove(KEY_PREVIOUS_FILTER).apply()
            } else {
                Timber.w("Filter jangan ganggu setelah crash belum bisa dipulihkan; dicoba lagi nanti")
            }
        }
    }
}
