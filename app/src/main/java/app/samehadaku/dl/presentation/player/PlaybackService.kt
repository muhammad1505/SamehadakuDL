package app.samehadaku.dl.presentation.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.samehadaku.dl.MainActivity
import app.samehadaku.dl.R

/**
 * Foreground media service for the internal player.
 *
 * The service keeps the same ExoPlayer instance that the player screen draws, so
 * decoder switching, the equalizer and track overrides stay untouched. Wrapping
 * it in a MediaSession is what makes background playback survive the activity
 * going away and gives the Picture-in-Picture window and the media notification
 * working play/pause/seek controls.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_stat_download)
            },
        )
        val player = PlaybackEngine.playerForService(this)
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
        mediaSession = session
        PlaybackEngine.attachSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Swiping the app away keeps audio going while a video is still playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        PlaybackEngine.onServiceStopped(mediaSession)
        mediaSession = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
