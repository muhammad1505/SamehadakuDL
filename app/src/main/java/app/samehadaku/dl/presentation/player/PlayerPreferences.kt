package app.samehadaku.dl.presentation.player

import android.content.Context
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Player rules the user sets stay in place for the next video. Opening another
 * episode used to snap the scale back to "Pas" and the speed back to 1x, which
 * forced the same adjustment on every playback.
 */
internal class PlayerPreferences(context: Context) {

    private val preferences =
        context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)

    var resizeMode: Int
        get() = preferences.getInt(KEY_RESIZE_MODE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        set(value) = preferences.edit().putInt(KEY_RESIZE_MODE, value).apply()

    var speed: Float
        get() = preferences.getFloat(KEY_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
        set(value) = preferences.edit().putFloat(KEY_SPEED, value).apply()

    var subtitleSize: Float
        get() = preferences.getFloat(KEY_SUBTITLE_SIZE, 1f).coerceIn(MIN_SUBTITLE_SIZE, MAX_SUBTITLE_SIZE)
        set(value) = preferences.edit().putFloat(KEY_SUBTITLE_SIZE, value).apply()

    var subtitleBottom: Float
        get() = preferences.getFloat(KEY_SUBTITLE_BOTTOM, 0f).coerceIn(0f, MAX_SUBTITLE_BOTTOM)
        set(value) = preferences.edit().putFloat(KEY_SUBTITLE_BOTTOM, value).apply()

    private companion object {
        const val MIN_SPEED = 0.75f
        const val MAX_SPEED = 2f
        const val MIN_SUBTITLE_SIZE = 0.6f
        const val MAX_SUBTITLE_SIZE = 2.5f
        const val MAX_SUBTITLE_BOTTOM = 0.45f
        const val KEY_RESIZE_MODE = "resize_mode"
        const val KEY_SPEED = "speed"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_SUBTITLE_BOTTOM = "subtitle_bottom"
    }
}
