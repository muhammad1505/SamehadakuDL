package app.samehadaku.dl.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.theme.TextSecondary
import kotlinx.coroutines.delay
import timber.log.Timber

private val PlaybackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Pas",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Penuh",
)

/**
 * Full-screen internal player. The player reports real positions, so Smart
 * Storage gets a truthful completion signal and can resume where the user
 * stopped. Playback speed and video scaling are available from the top bar,
 * the system bars are hidden while this screen is open, and errors can be
 * retried without leaving the video.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember {
        mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }
    var playerError by remember { mutableStateOf<String?>(null) }

    fun leave() {
        viewModel.onStop(exoPlayer.currentPosition)
        onBack()
    }

    BackHandler(onBack = ::leave)

    LaunchedEffect(state.uri) {
        val uri = state.uri ?: return@LaunchedEffect
        playerError = null
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        if (state.resumeMs > 0L) exoPlayer.seekTo(state.resumeMs)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(2_000)
            if (exoPlayer.isPlaying) {
                val duration = exoPlayer.duration
                viewModel.onPosition(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = if (duration > 0L) duration else 0L,
                )
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) viewModel.onPlaybackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "Pemutar internal gagal memutar video")
                playerError = error.localizedMessage ?: "Video gagal diputar."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            viewModel.onStop(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Full-screen playback: hide the system bars and show them again on exit.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exoPlayer.pause()
        viewModel.onStop(exoPlayer.currentPosition)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (state.uri != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setKeepScreenOn(true)
                    }
                },
                update = { playerView -> playerView.resizeMode = resizeMode },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali dari pemutar",
                onClick = ::leave,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.folderLabel.isNotBlank()) {
                    Text(
                        text = state.folderLabel,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            GlassButton(
                text = speedLabel(speed),
                onClick = { speed = nextSpeed(speed) },
                height = 32.dp,
            )
            Spacer(Modifier.width(6.dp))
            GlassButton(
                text = resizeLabel(resizeMode),
                onClick = { resizeMode = nextResizeMode(resizeMode) },
                height = 32.dp,
            )
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        val errorMessage = state.error ?: playerError
        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playerError != null) {
                        GlassButton(
                            text = "Coba lagi",
                            onClick = {
                                playerError = null
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            },
                        )
                    }
                    GlassButton(text = "Kembali", onClick = ::leave)
                }
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun nextSpeed(current: Float): Float {
    val index = PlaybackSpeeds.indexOf(current)
    return PlaybackSpeeds[(index + 1).coerceAtLeast(0) % PlaybackSpeeds.size]
}

private fun resizeLabel(mode: Int): String =
    ResizeModes.firstOrNull { it.first == mode }?.second ?: "Pas"

private fun nextResizeMode(current: Int): Int {
    val index = ResizeModes.indexOfFirst { it.first == current }
    return ResizeModes[(index + 1).coerceAtLeast(0) % ResizeModes.size].first
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
