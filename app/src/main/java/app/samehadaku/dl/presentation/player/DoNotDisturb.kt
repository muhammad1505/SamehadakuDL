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
 * The filter that was active before is remembered and restored as soon as the
 * player closes, pauses, or goes to the background, so the phone is never left
 * silenced after watching.
 *
 * The filter used is "prioritas", not "total senyap": total silence also mutes
 * media on many devices, so enabling it left the video playing without sound.
 * Priority keeps notifications quiet while the movie's audio stays audible.
 */
internal class DoNotDisturb(private val context: Context) {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private var previousFilter: Int? = null

    fun hasAccess(): Boolean = manager?.isNotificationPolicyAccessGranted == true

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
        if (previousFilter == null) {
            previousFilter = runCatching { nm.currentInterruptionFilter }
                .getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        val activated = runCatching {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }.isSuccess
        if (!activated) previousFilter = null
        return activated
    }

    /** Restores the interruption filter that was active before [activate]. */
    fun release() {
        val nm = manager ?: return
        val restore = previousFilter ?: return
        previousFilter = null
        runCatching { nm.setInterruptionFilter(restore) }
            .onFailure { Timber.w(it, "Filter jangan ganggu tidak bisa dipulihkan") }
    }
}
