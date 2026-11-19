package app.samehadaku.dl.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state between the player screen and MainActivity for Picture-in-Picture.
 *
 * The player is a Compose destination inside the single activity, so the
 * activity needs to mirror when the PiP window is active and how wide the video
 * is when the window is created.
 */
internal object PlayerPip {
    var aspectRatio by mutableFloatStateOf(16f / 9f)
    var inPip by mutableStateOf(false)

    fun enter(activity: Activity?) {
        val target = activity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (target.isInPictureInPictureMode) return
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        // Android rejects PiP aspect ratios outside 0.418..2.39. A vertical clip
        // or an ultrawide file would otherwise make enterPictureInPictureMode
        // throw and the PiP button would silently do nothing.
        val clamped = ratio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
            .build()
        runCatching { target.enterPictureInPictureMode(params) }
    }

    private const val MIN_ASPECT_RATIO = 0.42f
    private const val MAX_ASPECT_RATIO = 2.39f
}
