package app.samehadaku.dl.presentation.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import timber.log.Timber

/** Decoder order of the top-bar chip: Auto -> Hardware -> Software. */
internal enum class DecoderMode(
    val label: String,
    val badge: String,
    val description: String,
) {
    AUTO("Auto", "AUTO", "Pilih dekoder terbaik, fallback otomatis"),
    HARDWARE("Hardware", "HW", "Hemat baterai & performa tinggi"),
    SOFTWARE("Software", "SW", "Kompatibel untuk codec berat (HEVC 10-bit / AV1)"),
    ;

    fun next(): DecoderMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(value: String): DecoderMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Same-process bridge between the player screen and [PlaybackService].
 *
 * ExoPlayer stays owned by the screen so decoder switching, equalizer session
 * tracking and track selection keep their direct API. The service wraps the same
 * player in a MediaSession, which promotes playback to a foreground media
 * service (reliable background audio while the app is at home or the screen is
 * off, not after the task is swiped away) and gives PiP/notification controls a
 * session to drive.
 */
internal object PlaybackEngine {

    private var player: ExoPlayer? = null
    private var engineCreatedPlayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var started = false

    val currentPlayer: ExoPlayer? get() = player

    /** Called by the screen whenever it creates or switches the player. */
    fun register(context: Context, newPlayer: ExoPlayer) {
        player = newPlayer
        session?.player = newPlayer
        engineCreatedPlayer?.let { placeholder ->
            if (placeholder !== newPlayer) runCatching { placeholder.release() }
            engineCreatedPlayer = null
        }
        if (!started) {
            started = runCatching {
                // The activity is in the foreground here; MediaSessionService
                // promotes itself to a media foreground service once playing.
                context.startService(Intent(context, PlaybackService::class.java))
            }.isSuccess
        }
    }

    fun attachSession(newSession: MediaSession) {
        session = newSession
        player?.let { newSession.player = it }
    }

    /** Released from [PlaybackService.onDestroy]. */
    fun detachSession(expected: MediaSession?) {
        val current = session
        if (expected != null && current === expected) {
            runCatching { current.release() }
            session = null
        }
    }

    /** Screen left: drop the session and stop the foreground service. */
    fun release(context: Context) {
        session?.let { runCatching { it.release() } }
        session = null
        player = null
        engineCreatedPlayer?.let { runCatching { it.release() } }
        engineCreatedPlayer = null
        started = false
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }

    /**
     * Service started without a screen (for example after a process/service
     * restart): give the session a player so the notification can exist.
     */
    fun playerForService(context: Context): ExoPlayer {
        player?.let { return it }
        val mode = DecoderMode.from(PlayerPreferences(context).decoderMode)
        return buildPlayer(context, mode).also {
            player = it
            engineCreatedPlayer = it
        }
    }

    fun onServiceStopped(expected: MediaSession?) {
        detachSession(expected)
        started = false
        // A placeholder created only for a session without a screen must not
        // keep decoding in the background after the service is gone.
        val placeholder = engineCreatedPlayer
        if (placeholder != null) {
            if (player === placeholder) player = null
            engineCreatedPlayer = null
            runCatching { placeholder.release() }
        }
        Timber.i("Pemutaran latar dihentikan")
    }
}

@OptIn(UnstableApi::class)
internal fun buildPlayer(context: Context, mode: DecoderMode): ExoPlayer =
    ExoPlayer.Builder(context, buildRenderers(context, mode)).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
        // The files are local, so a local wake lock is enough to keep background
        // audio decoding while the screen is off. It is only held during playback
        // and is released on pause/stop.
        setWakeMode(C.WAKE_MODE_LOCAL)
    }

@OptIn(UnstableApi::class)
private fun buildRenderers(context: Context, mode: DecoderMode): DefaultRenderersFactory {
    val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    if (mode == DecoderMode.AUTO) return factory
    val wantHardware = mode == DecoderMode.HARDWARE
    val selector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunneling,
        )
        all.filter { it.hardwareAccelerated == wantHardware }.ifEmpty { all }
    }
    return factory.setMediaCodecSelector(selector)
}
