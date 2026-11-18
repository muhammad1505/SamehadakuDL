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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.util.formatBytes
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

private enum class PlayerSheet { NONE, EPISODES, SUBTITLE }

/**
 * Full-screen internal player. The player reports real positions, so Smart
 * Storage gets a truthful completion signal and can resume where the user
 * stopped. Beyond the basic controls the screen offers:
 *
 * - the whole collection folder as an episode playlist, with previous/next
 *   buttons and a list to jump to any episode,
 * - display rules (scale, speed, subtitle layout) that survive the next episode,
 * - hardware decoding with an automatic software fallback,
 * - pinch to zoom and drag to pan the video surface,
 * - external subtitles (SRT, WebVTT, SSA/ASS) behind an options panel instead of
 *   jumping straight into the file picker, with pinch/vertical-drag gestures for
 *   text size and position once subtitle gesture mode is on,
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
    val preferences = remember(context) { PlayerPreferences(context) }
    var speed by remember { mutableFloatStateOf(preferences.speed) }
    var resizeMode by remember { mutableIntStateOf(preferences.resizeMode) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleMime by remember { mutableStateOf<String?>(null) }
    var subtitleLabel by remember { mutableStateOf<String?>(null) }
    var subtitleMode by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableFloatStateOf(preferences.subtitleSize) }
    var subtitleBottom by remember { mutableFloatStateOf(preferences.subtitleBottom) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }
    var sheet by remember { mutableStateOf(PlayerSheet.NONE) }
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

    fun openEntry(index: Int) {
        sheet = PlayerSheet.NONE
        if (index == state.index) return
        viewModel.playIndex(index, exoPlayer.currentPosition)
        // A different episode starts clean: the previous zoom and side-loaded
        // subtitle belong to the old file.
        zoom = 1f
        panX = 0f
        panY = 0f
        subtitleUri = null
        subtitleMime = null
        subtitleLabel = null
        subtitleMode = false
        applySurfaceTransform()
        controlsVisible = true
    }

    fun replay() {
        ended = false
        exoPlayer.seekTo(0L)
        exoPlayer.play()
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
                showHint(if (dndActive) "Notifikasi ditahan" else "Jangan ganggu gagal aktif")
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
        subtitleLabel = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment
        subtitleMode = false
        showHint("Subtitle dimuat")
    }

    BackHandler {
        if (sheet != PlayerSheet.NONE) sheet = PlayerSheet.NONE else leave()
    }

    LaunchedEffect(hintToken) {
        if (hint != null) {
            delay(1_800)
            hint = null
        }
    }

    // Display rules are remembered, so the next episode opens with the same
    // scale, speed and subtitle layout the user already chose.
    LaunchedEffect(resizeMode) { preferences.resizeMode = resizeMode }
    LaunchedEffect(speed) {
        preferences.speed = speed
        exoPlayer.setPlaybackSpeed(speed)
    }
    LaunchedEffect(subtitleSize) { preferences.subtitleSize = subtitleSize }
    LaunchedEffect(subtitleBottom) { preferences.subtitleBottom = subtitleBottom }
    LaunchedEffect(subtitleSize, subtitleBottom) { applySurfaceTransform() }

    // Rebuild the media item when the episode or the attached subtitle changes,
    // keeping the position the user was already watching for a subtitle swap.
    LaunchedEffect(state.uri, subtitleUri, subtitleMime) {
        val videoUri = state.uri ?: return@LaunchedEffect
        val sameItem = loadedUri == videoUri
        playerError = null
        ended = false
        val resume = if (sameItem && exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentPosition
        } else {
            state.resumeMs
        }
        exoPlayer.setMediaItem(buildMediaItem(videoUri, subtitleUri, subtitleMime))
        exoPlayer.prepare()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        if (resume > 0L) exoPlayer.seekTo(resume)
        exoPlayer.playWhenReady = true
        loadedUri = videoUri
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
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
                ended = playbackState == Player.STATE_ENDED
                if (ended) viewModel.onPlaybackEnded()
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

    LaunchedEffect(controlsVisible, isPlaying, scrubbing, sheet) {
        if (controlsVisible && isPlaying && !scrubbing && sheet == PlayerSheet.NONE) {
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

        if (state.loading || (buffering && playerError == null && state.uri != null && !ended)) {
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
                    .padding(bottom = 150.dp, start = 16.dp, end = 16.dp)
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

        if (controlsVisible && state.uri != null && !ended) {
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
                        Text(
                            text = buildString {
                                if (state.folderLabel.isNotBlank()) append(state.folderLabel)
                                if (state.entries.size > 1) {
                                    if (isNotEmpty()) append(" • ")
                                    append("Ep ${state.index + 1} dari ${state.entries.size}")
                                }
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    if (state.entries.size > 1) {
                        GlassButton(
                            text = "Ep ${state.index + 1}/${state.entries.size}",
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            onClick = { sheet = PlayerSheet.EPISODES },
                            tint = BrandMint,
                            height = 32.dp,
                        )
                    }
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
                        onClick = { sheet = PlayerSheet.SUBTITLE },
                        tint = if (subtitleUri != null) BrandMint else Color.White,
                        height = 32.dp,
                    )
                }
            }
        }

        if (controlsVisible && state.uri != null && !ended) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(if (scrubbing) scrubPosition else positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(50.dp),
                    )
                    PlayerScrubber(
                        value = (if (scrubbing) scrubPosition else positionMs)
                            .toFloat()
                            .coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                        duration = durationMs.coerceAtLeast(1L).toFloat(),
                        buffered = bufferedMs.toFloat(),
                        onScrub = {
                            scrubbing = true
                            scrubPosition = it.toLong()
                        },
                        onScrubFinished = {
                            seekTo(scrubPosition)
                            scrubbing = false
                        },
                        enabled = durationMs > 0L,
                        activeColor = BrandMint,
                        thumbColor = BrandMint,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentDescription = "Posisi pemutaran video",
                        stateDescription = "${formatTime(if (scrubbing) scrubPosition else positionMs)} " +
                            "dari ${formatTime(durationMs)}",
                    )
                    Text(
                        text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(50.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Episode sebelumnya",
                        onClick = { openEntry(state.index - 1) },
                        enabled = state.hasPrevious,
                        tint = Color.White,
                        size = 42.dp,
                        iconSize = 24.dp,
                    )
                    Spacer(Modifier.width(20.dp))
                    GlassIconButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        tint = BrandMint,
                        size = 54.dp,
                        iconSize = 28.dp,
                    )
                    Spacer(Modifier.width(20.dp))
                    GlassIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Episode berikutnya",
                        onClick = { openEntry(state.index + 1) },
                        enabled = state.hasNext,
                        tint = Color.White,
                        size = 42.dp,
                        iconSize = 24.dp,
                    )
                }
            }
        }

        if (ended && playerError == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (state.hasNext) "Episode selesai" else "Semua episode selesai",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Putar ulang",
                        icon = Icons.Rounded.Replay,
                        onClick = ::replay,
                        height = 38.dp,
                    )
                    if (state.hasNext) {
                        GlassButton(
                            text = "Episode berikutnya",
                            icon = Icons.Rounded.SkipNext,
                            onClick = { openEntry(state.index + 1) },
                            tint = BrandMint,
                            height = 38.dp,
                        )
                    }
                }
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

        when (sheet) {
            PlayerSheet.EPISODES -> PlayerPanel(
                title = "Daftar episode",
                subtitle = "${state.entries.size} episode • ${state.folderLabel}",
                onDismiss = { sheet = PlayerSheet.NONE },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                ) {
                    itemsIndexed(
                        state.entries,
                        key = { _, entry -> entry.fileName },
                    ) { index, entry ->
                        val current = index == state.index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openEntry(index) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (current) BrandMint else TextMuted,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(30.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (current) "Sedang diputar" else formatBytes(entry.size),
                                    color = if (current) BrandMint else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (current) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = BrandMint,
                                )
                            }
                        }
                    }
                }
            }

            PlayerSheet.SUBTITLE -> PlayerPanel(
                title = "Subtitle",
                subtitle = subtitleLabel ?: "Belum ada subtitle eksternal",
                onDismiss = { sheet = PlayerSheet.NONE },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(
                        text = if (subtitleUri == null) "Pilih file subtitle" else "Ganti file subtitle",
                        icon = Icons.Rounded.FolderOpen,
                        onClick = {
                            sheet = PlayerSheet.NONE
                            runCatching { subtitlePicker.launch(arrayOf("*/*")) }
                                .onFailure { showHint("Pemilih file tidak bisa dibuka") }
                        },
                        height = 44.dp,
                    )
                    if (subtitleUri != null) {
                        GlassButton(
                            text = "Lepas subtitle",
                            icon = Icons.Rounded.Close,
                            onClick = {
                                subtitleUri = null
                                subtitleMime = null
                                subtitleLabel = null
                                subtitleMode = false
                                sheet = PlayerSheet.NONE
                                showHint("Subtitle dilepas")
                            },
                            height = 44.dp,
                        )
                    }
                    Text(
                        text = "Ukuran teks",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f to "Kecil", 1f to "Normal", 1.3f to "Besar").forEach { (value, label) ->
                            GlassButton(
                                text = label,
                                onClick = {
                                    subtitleSize = value
                                    applySurfaceTransform()
                                },
                                tint = if (subtitleSize == value) BrandMint else Color.White,
                                height = 34.dp,
                            )
                        }
                    }
                    Text(
                        text = "Posisi",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0f to "Bawah", 0.2f to "Tengah", 0.4f to "Atas").forEach { (value, label) ->
                            GlassButton(
                                text = label,
                                onClick = {
                                    subtitleBottom = value
                                    applySurfaceTransform()
                                },
                                tint = if (subtitleBottom == value) BrandMint else Color.White,
                                height = 34.dp,
                            )
                        }
                    }
                    GlassButton(
                        text = if (subtitleMode) "Mode gestur aktif" else "Atur ukuran lewat gestur",
                        icon = Icons.Rounded.Subtitles,
                        onClick = {
                            subtitleMode = !subtitleMode
                            sheet = PlayerSheet.NONE
                            if (subtitleMode) {
                                showHint("Cubit untuk ukuran, geser untuk posisi")
                            }
                        },
                        tint = if (subtitleMode) BrandMint else Color.White,
                        height = 44.dp,
                    )
                    Text(
                        text = "Subtitle eksternal didukung dalam format SRT, WebVTT, dan SSA/ASS. " +
                            "Ukuran dan posisi terakhir dipakai lagi di episode berikutnya.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            PlayerSheet.NONE -> Unit
        }
    }
}

/**
 * Bottom panel over the video. The scrim closes it on tap; taps inside the
 * surface stop there so buttons and list rows keep working.
 */
@Composable
private fun PlayerPanel(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
            color = SlateSurface,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 16.dp, bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassButton(
                        text = "Tutup",
                        onClick = onDismiss,
                        height = 34.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = 20.dp)) { content() }
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
