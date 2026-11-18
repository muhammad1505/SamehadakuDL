package app.samehadaku.dl.presentation.settings

import android.content.Context

/**
 * Remembers the one background switch Android cannot read back: the OEM
 * autostart toggle. Visiting the OEM screen is recorded, and the status only
 * turns green after the user confirms it on return.
 */
internal class OptimizationPreferences(context: Context) {

    private val preferences =
        context.getSharedPreferences("system_optimization", Context.MODE_PRIVATE)

    var autoStartConfirmed: Boolean
        get() = preferences.getBoolean(KEY_AUTOSTART_CONFIRMED, false)
        set(value) = preferences.edit().putBoolean(KEY_AUTOSTART_CONFIRMED, value).apply()

    var autoStartPending: Boolean
        get() = preferences.getBoolean(KEY_AUTOSTART_PENDING, false)
        set(value) = preferences.edit().putBoolean(KEY_AUTOSTART_PENDING, value).apply()

    private companion object {
        const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
        const val KEY_AUTOSTART_PENDING = "autostart_pending"
    }
}
