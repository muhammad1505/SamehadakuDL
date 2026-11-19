package app.samehadaku.dl.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
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

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Returns false when PiP is unavailable or the system rejected the request,
     * so the player can show an explanation instead of a button that does
     * nothing.
     */
    fun enter(activity: Activity?): Boolean {
        val target = activity ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (target.isInPictureInPictureMode) return true
        val clamped = clampPipAspectRatio(aspectRatio)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
            .build()
        return runCatching { target.enterPictureInPictureMode(params) }.getOrDefault(false)
    }
}
