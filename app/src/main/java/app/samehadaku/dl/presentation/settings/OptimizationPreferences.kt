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

    /**
     * Legacy ColorOS can keep the framework battery exemption off even after its
     * own "allow background activity" switch is on. The user confirms that OEM
     * switch once, mirroring the autostart flow.
     */
    var batteryConfirmed: Boolean
        get() = preferences.getBoolean(KEY_BATTERY_CONFIRMED, false)
        set(value) = preferences.edit().putBoolean(KEY_BATTERY_CONFIRMED, value).apply()

    var batteryPending: Boolean
        get() = preferences.getBoolean(KEY_BATTERY_PENDING, false)
        set(value) = preferences.edit().putBoolean(KEY_BATTERY_PENDING, value).apply()

    private companion object {
        const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
        const val KEY_AUTOSTART_PENDING = "autostart_pending"
        const val KEY_BATTERY_CONFIRMED = "battery_confirmed"
        const val KEY_BATTERY_PENDING = "battery_pending"
    }
}
