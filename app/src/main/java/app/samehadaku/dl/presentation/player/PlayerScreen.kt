package app.samehadaku.dl.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.samehadaku.dl.R
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.TextSecondary
import kotlinx.coroutines.delay
import timber.log.Timber

private val PlaybackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Pas",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Penuh",
)
private const val MAX_ZOOM = 4f
private const val SEEK_STEP_MS = 10_000L

/**
 * Full-screen internal player. The player reports real positions, so Smart
 * Storage gets a truthful completion signal and can resume where the user
 * stopped. Beyond the basic controls the screen offers:
 *
 * - hardware decoding with an automatic software fallback (ExoPlayer picks the
 *   platform decoder first; MediaCodec itself uses every core the codec allows),
 * - pinch to zoom and drag to pan the video surface,
 * - external subtitles (SRT, WebVTT, SSA/ASS) with pinch/vertical-drag gestures
 *   for text size and vertical position once subtitle gesture mode is on,
 * - speed, scaling and rotation controls, plus the system Do Not Disturb filter
 *   while the user watches, restored on exit,
 * - double-tap to seek 10 seconds back or forward.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exoPlayer = remember {
        // Hardware decoding is the default; the fallback keeps a broken or
        // unsupported hardware codec from ending playback with an error.
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers).build().apply {
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
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleMime by remember { mutableStateOf<String?>(null) }
    var subtitleMode by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableFloatStateOf(1f) }
    var subtitleBottom by remember { mutableFloatStateOf(0f) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintToken by remember { mutableIntStateOf(0) }
    val dnd = remember(context) { DoNotDisturb(context) }
    var dndActive by remember { mutableStateOf(false) }
    var dndPending by remember { mutableStateOf(false) }
    var dndGranted by remember { mutableStateOf(dnd.hasAccess()) }

    fun showHint(message: String) {
        hint = message
        hintToken++
    }

    // Gestures change state far more often than the screen recomposes, and
    // AndroidView's update block only runs on recomposition. Apply the surface
    // transform right away so pinch-zoom, pan and subtitle sizing feel live.
    fun applySurfaceTransform() {
        val playerView = playerViewRef ?: return
        playerView.videoSurfaceView?.let { surface ->
            surface.scaleX = zoom
            surface.scaleY = zoom
            val maxX = playerView.width * (zoom - 1f) / 2f
            val maxY = playerView.height * (zoom - 1f) / 2f
            surface.translationX = panX.coerceIn(-maxX, maxX)
            surface.translationY = panY.coerceIn(-maxY, maxY)
        }
        playerView.subtitleView?.apply {
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleSize)
            setBottomPaddingFraction(subtitleBottom)
        }
    }

    fun toggleDnd() {
        when {
            dndActive -> {
                dnd.release()
                dndActive = false
                showHint("Jangan ganggu dimatikan")
            }
            dnd.hasAccess() -> {
                dndActive = dnd.activate()
                dndGranted = dndActive
                showHint(if (dndActive) "Jangan ganggu aktif" else "Jangan ganggu gagal aktif")
            }
            else -> {
                dndPending = true
                showHint("Izinkan akses Jangan ganggu di pengaturan")
                dnd.requestAccess()
            }
        }
    }

    fun leave() {
        viewModel.onStop(exoPlayer.currentPosition)
        onBack()
    }

    fun seekTo(targetMs: Long) {
        val duration = exoPlayer.duration
        val target = if (duration > 0L) targetMs.coerceIn(0L, duration) else targetMs.coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        positionMs = target
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val mime = subtitleMimeType(context, uri)
        if (mime == null) {
            showHint("Format subtitle belum didukung. Pakai SRT, VTT, atau SSA/ASS.")
            return@rememberLauncherForActivityResult
        }
        subtitleMime = mime
        subtitleUri = uri
        subtitleMode = false
        showHint("Subtitle dimuat")
    }

    BackHandler(onBack = ::leave)

    LaunchedEffect(hintToken) {
        if (hint != null) {
            delay(1_800)
            hint = null
        }
    }

    // Rebuild the media item when a subtitle is attached, keeping the position
    // the user was already watching.
    LaunchedEffect(state.uri, subtitleUri, subtitleMime) {
        val videoUri = state.uri ?: return@LaunchedEffect
        playerError = null
        val resume = if (exoPlayer.mediaItemCount > 0) exoPlayer.currentPosition else state.resumeMs
        exoPlayer.setMediaItem(buildMediaItem(videoUri, subtitleUri, subtitleMime))
        exoPlayer.prepare()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        if (resume > 0L) exoPlayer.seekTo(resume)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (!scrubbing) positionMs = exoPlayer.currentPosition
            if (exoPlayer.isPlaying) {
                viewModel.onPosition(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = durationMs,
                )
            }
            delay(500)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
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

    // Video follows the device sensor while this screen is open, with an explicit
    // landscape lock for devices whose rotation is locked. MainActivity handles the
    // orientation change itself, so ExoPlayer keeps its position and buffer.
    val activity = view.context.findActivity()
    var forceLandscape by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(activity, forceLandscape) {
        activity?.requestedOrientation = if (forceLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    // Rotating through a handled configuration change does not recreate the
    // activity, so the bars have to be hidden again for the new orientation.
    val orientation = LocalConfiguration.current.orientation
    LaunchedEffect(orientation) {
        val window = activity?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(dnd) {
        onDispose { dnd.release() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        dndGranted = dnd.hasAccess()
        if (dndPending && dndGranted) {
            dndPending = false
            dndActive = dnd.activate()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exoPlayer.pause()
        viewModel.onStop(exoPlayer.currentPosition)
        dnd.release()
        dndActive = false
    }

    LaunchedEffect(controlsVisible, isPlaying, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(3_500)
            controlsVisible = false
        }
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
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                        player = exoPlayer
                        setKeepScreenOn(true)
                        playerViewRef = this
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = resizeMode
                    applySurfaceTransform()
                },
            )
        }

        // Gesture surface: single tap toggles the controls, double tap seeks,
        // pinch zooms (or resizes subtitles in subtitle gesture mode) and a drag
        // pans the video / moves the subtitle text.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(subtitleMode) {
                    if (subtitleMode) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            subtitleSize = (subtitleSize * gestureZoom).coerceIn(0.6f, 2.5f)
                            subtitleBottom = (subtitleBottom - pan.y / size.height)
                                .coerceIn(0f, 0.45f)
                            applySurfaceTransform()
                        }
                    } else {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            if (zoom > 1f) {
                                val maxX = size.width * (zoom - 1f) / 2f
                                val maxY = size.height * (zoom - 1f) / 2f
                                panX = (panX + pan.x).coerceIn(-maxX, maxX)
                                panY = (panY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                panX = 0f
                                panY = 0f
                            }
                            applySurfaceTransform()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2f
                            seekTo(exoPlayer.currentPosition + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                            showHint(if (forward) "+10 detik" else "-10 detik")
                        },
                    )
                },
        )

        if (state.loading || (buffering && playerError == null && state.uri != null)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        if (subtitleMode) {
            Text(
                text = "Cubit: ukuran subtitle • Geser naik/turun: posisi teks",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        hint?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    GlassIconButton(
                        icon = if (dndActive) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = if (dndActive) {
                            "Matikan Jangan ganggu"
                        } else {
                            "Aktifkan Jangan ganggu"
                        },
                        onClick = ::toggleDnd,
                        tint = if (dndActive) BrandMint else Color.White,
                        size = 36.dp,
                        iconSize = 19.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(
                        icon = Icons.Rounded.ScreenRotation,
                        contentDescription = if (forceLandscape) {
                            "Ikuti rotasi perangkat"
                        } else {
                            "Kunci layar landscape"
                        },
                        onClick = { forceLandscape = !forceLandscape },
                        tint = if (forceLandscape) BrandMint else Color.White,
                        size = 36.dp,
                        iconSize = 19.dp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    GlassButton(
                        text = speedLabel(speed),
                        onClick = { speed = nextSpeed(speed) },
                        tint = Color.White,
                        height = 32.dp,
                    )
                    GlassButton(
                        text = resizeLabel(resizeMode),
                        onClick = { resizeMode = nextResizeMode(resizeMode) },
                        tint = Color.White,
                        height = 32.dp,
                    )
                    GlassButton(
                        text = when {
                            subtitleUri == null -> "Subtitle"
                            subtitleMode -> "Subtitle: gestur"
                            else -> "Subtitle: aktif"
                        },
                        icon = Icons.Rounded.Subtitles,
                        onClick = {
                            if (subtitleUri == null) {
                                subtitlePicker.launch(arrayOf("*/*"))
                            } else {
                                subtitleMode = !subtitleMode
                            }
                        },
                        tint = if (subtitleUri != null) BrandMint else Color.White,
                        height = 32.dp,
                    )
                }
            }
        }

        if (controlsVisible && state.uri != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    size = 36.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTime(if (scrubbing) scrubPosition else positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = (if (scrubbing) scrubPosition else positionMs)
                        .toFloat()
                        .coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = {
                        scrubbing = true
                        scrubPosition = it.toLong()
                    },
                    onValueChangeFinished = {
                        seekTo(scrubPosition)
                        scrubbing = false
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = BrandMint,
                        activeTrackColor = BrandMint,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
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

private fun buildMediaItem(
    videoUri: Uri,
    subtitleUri: Uri?,
    subtitleMime: String?,
): MediaItem {
    val builder = MediaItem.Builder().setUri(videoUri)
    if (subtitleUri != null && subtitleMime != null) {
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(subtitleMime)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setLabel("Subtitle")
                    .build(),
            ),
        )
    }
    return builder.build()
}

/** Media3 only decodes these subtitle formats from a side-loaded file. */
private fun subtitleMimeType(context: Context, uri: Uri): String? {
    val name = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
        ?: uri.lastPathSegment.orEmpty()
    return when (name.substringAfterLast('.', "").lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        else -> null
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
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
    else -> (this as? ContextWrapper)?.baseContext?.findActivity()
}
