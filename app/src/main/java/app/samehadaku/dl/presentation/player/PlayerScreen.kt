package app.samehadaku.dl.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.samehadaku.dl.R
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.SystemOptimizationHint
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.util.formatBytes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_SWIPE_MS = 90_000f
private const val AUTO_NEXT_SECONDS = 5
private const val MAX_SUBTITLE_SYNC_MS = 5_000L
private const val HUD_HIDE_DELAY_MS = 3_000L
private val PanelWidth = 285.dp

private val PlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "FIT",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ZOOM",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "FILL",
)
private val SubtitleSizes = listOf(0.85f to "S", 1f to "M", 1.3f to "L")

private enum class PanelTab(val label: String, val icon: ImageVector) {
    EPISODES("Eps", Icons.AutoMirrored.Rounded.PlaylistPlay),
    SUBTITLE("CC", Icons.Rounded.ClosedCaption),
    AUDIO("Audio", Icons.Rounded.GraphicEq),
    SETTINGS("Setelan", Icons.Rounded.Settings),
}

private enum class SleepOption(val minutes: Int, val label: String) {
    OFF(0, "Mati"),
    MIN_15(15, "15 mnt"),
    MIN_30(30, "30 mnt"),
    MIN_60(60, "60 mnt"),
    ;

    fun next(): SleepOption = entries[(ordinal + 1) % entries.size]
}

private enum class SubtitleMode { AUTO, OFF, EMBEDDED, EXTERNAL }

private data class SubtitleTrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val id: String?,
)

private enum class LevelKind { BRIGHTNESS, VOLUME }

private data class LevelOverlay(val kind: LevelKind, val value: Float)

private data class SeekPreview(val targetMs: Long, val deltaMs: Long)

private data class PlayerBanner(
    val icon: ImageVector,
    val title: String,
    val message: String,
)

private data class AudioOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
)

private enum class DragAxis { NONE, SEEK, BRIGHTNESS, VOLUME }

/**
 * Full-screen internal player.
 *
 * Controls are split into four zones so nothing overlaps the middle of the
 * picture: a top bar with the back button, title, decoder, playlist and PiP; a
 * centre transport row with ±10s; a bottom bar with lock, time, subtitle, audio,
 * aspect, settings and fullscreen; and a 285dp glass panel on the right with
 * playlist, subtitle, audio and settings tabs. Every settings button carries a
 * short status badge and shows a rich banner describing the mode it switched to.
 *
 * Gestures: vertical drag on the left half sets brightness, on the right half
 * sets system volume, horizontal drag scrubs with a time preview, single tap
 * toggles the HUD and double tap on either side seeks ±10 seconds. Screen lock
 * disables every touch apart from the unlock capsule.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val preferences = remember(context) { PlayerPreferences(context) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var decoderMode by remember { mutableStateOf(DecoderMode.from(preferences.decoderMode)) }
    val exoPlayer = remember(decoderMode) { buildPlayer(context, decoderMode) }
    val audioEffects = remember { PlayerAudioEffects() }

    // Hand the player to the foreground media service; this is what keeps audio
    // alive in the background and lets PiP/the notification drive playback.
    LaunchedEffect(exoPlayer) {
        PlaybackEngine.register(context, exoPlayer)
    }

    var speed by remember { mutableFloatStateOf(preferences.speed) }
    var resizeMode by remember { mutableIntStateOf(preferences.resizeMode) }
    var eqPreset by remember {
        mutableStateOf(EqPreset.entries.firstOrNull { it.name == preferences.eqPreset } ?: EqPreset.NORMAL)
    }
    var boostPercent by remember { mutableIntStateOf(preferences.loudnessBoost) }
    var backgroundPlay by remember { mutableStateOf(preferences.backgroundPlay) }
    var subtitleSize by remember { mutableFloatStateOf(preferences.subtitleSize) }
    var subtitleSyncMs by remember { mutableLongStateOf(preferences.subtitleSyncMs) }

    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }
    var pendingResumeMs by remember { mutableLongStateOf(-1L) }
    var pendingPlayWhenReady by remember { mutableStateOf<Boolean?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var unlockVisible by remember { mutableStateOf(false) }
    var unlockToken by remember { mutableIntStateOf(0) }
    var panelTab by remember { mutableStateOf<PanelTab?>(null) }
    var lastPanelTab by remember { mutableStateOf(PanelTab.EPISODES) }
    var banner by remember { mutableStateOf<PlayerBanner?>(null) }
    var bannerToken by remember { mutableIntStateOf(0) }
    var levelOverlay by remember { mutableStateOf<LevelOverlay?>(null) }
    var seekPreview by remember { mutableStateOf<SeekPreview?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var fullscreen by remember { mutableStateOf(false) }
    var autoNextCancelled by remember { mutableStateOf(false) }
    var autoNextCountdown by remember { mutableIntStateOf(-1) }
    var sleepOption by remember { mutableStateOf(SleepOption.OFF) }

    var audioOptions by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    var selectedAudio by remember { mutableStateOf<AudioOption?>(null) }
    var videoHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var qualityHeight by remember { mutableIntStateOf(0) }

    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleLabel by remember { mutableStateOf<String?>(null) }
    var subtitleCues by remember { mutableStateOf<List<TextCue>>(emptyList()) }
    var subtitleLoading by remember { mutableStateOf(false) }
    var subtitleMode by remember { mutableStateOf(SubtitleMode.AUTO) }
    var embeddedSubtitles by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var selectedEmbeddedSubtitle by remember { mutableStateOf<SubtitleTrackOption?>(null) }

    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f,
        )
    }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val dnd = remember(context) { DoNotDisturb(context) }
    var dndActive by remember { mutableStateOf(false) }
    var dndPending by remember { mutableStateOf(false) }
    var dndGranted by remember { mutableStateOf(dnd.hasAccess()) }

    fun showBanner(icon: ImageVector, title: String, message: String) {
        banner = PlayerBanner(icon, title, message)
        bannerToken++
    }

    fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        val clamped = value.coerceIn(0.01f, 1f)
        val attributes = window.attributes
        attributes.screenBrightness = clamped
        window.attributes = attributes
        brightness = clamped
    }

    fun volumeFraction(): Float =
        if (maxVolume <= 0) 0f else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    fun setVolume(value: Float) {
        val target = (value.coerceIn(0f, 1f) * maxVolume).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun seekTo(targetMs: Long) {
        val duration = exoPlayer.duration
        val target = if (duration > 0L) targetMs.coerceIn(0L, duration) else targetMs.coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        positionMs = target
    }

    fun openEntry(index: Int) {
        panelTab = null
        if (index !in state.entries.indices || index == state.index) return
        ended = false
        autoNextCancelled = false
        viewModel.playIndex(index, exoPlayer.currentPosition)
        subtitleUri = null
        subtitleLabel = null
        subtitleCues = emptyList()
        subtitleMode = SubtitleMode.AUTO
        selectedEmbeddedSubtitle = null
        // The old media item's text override cannot apply to the next file.
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        controlsVisible = true
    }

    fun replay() {
        autoNextCancelled = true
        ended = false
        seekTo(0L)
        exoPlayer.play()
    }

    fun cycleDecoder() {
        pendingResumeMs = exoPlayer.currentPosition
        pendingPlayWhenReady = exoPlayer.playWhenReady
        // The override holds a TrackGroup from the old player; default to Auto
        // so the new player picks the embedded track again.
        if (subtitleMode == SubtitleMode.EMBEDDED) {
            subtitleMode = SubtitleMode.AUTO
            selectedEmbeddedSubtitle = null
        }
        val next = decoderMode.next()
        decoderMode = next
        preferences.decoderMode = next.name
        controlsVisible = true
        showBanner(Icons.Rounded.Memory, "Decoder: ${next.label} (${next.badge})", next.description)
    }

    fun cycleAspect() {
        resizeMode = nextResizeMode(resizeMode)
        showBanner(
            Icons.Rounded.AspectRatio,
            "Rasio: ${resizeLabel(resizeMode)}",
            when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Video memenuhi layar"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Penuhi layar tanpa distorsi"
                else -> "Seluruh video terlihat (letterbox)"
            },
        )
    }

    fun setEq(preset: EqPreset) {
        eqPreset = preset
        preferences.eqPreset = preset.name
        showBanner(Icons.Rounded.GraphicEq, "Audio: ${preset.label} (${preset.badge})", preset.description)
    }

    fun selectAudio(option: AudioOption?) {
        selectedAudio = option
        exoPlayer.trackSelectionParameters = if (option == null) {
            exoPlayer.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
        } else {
            exoPlayer.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                .build()
        }
        showBanner(
            Icons.Rounded.GraphicEq,
            "Track audio",
            option?.label ?: "Otomatis mengikuti file",
        )
    }

    fun setQuality(height: Int) {
        qualityHeight = height
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(
                Int.MAX_VALUE,
                if (height > 0) height else Int.MAX_VALUE,
            )
            .build()
        showBanner(
            Icons.Rounded.HighQuality,
            "Kualitas: ${if (height > 0) "${height}p" else "Auto"}",
            if (height > 0) "Batas resolusi ${height}p" else "Mengikuti resolusi terbaik file",
        )
    }

    fun selectEmbeddedSubtitle(option: SubtitleTrackOption) {
        subtitleUri = null
        subtitleLabel = null
        subtitleCues = emptyList()
        selectedEmbeddedSubtitle = option
        subtitleMode = SubtitleMode.EMBEDDED
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
            .build()
        showBanner(
            Icons.Rounded.ClosedCaption,
            "Subtitle: ${option.label}",
            "Track bawaan video dipilih",
        )
    }

    fun disableSubtitles() {
        subtitleUri = null
        subtitleLabel = null
        subtitleCues = emptyList()
        selectedEmbeddedSubtitle = null
        subtitleMode = SubtitleMode.OFF
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        showBanner(
            Icons.Rounded.ClosedCaption,
            "Subtitle dimatikan",
            "Tidak ada teks subtitle yang ditampilkan",
        )
    }

    fun useExternalSubtitle() {
        if (subtitleCues.isEmpty()) return
        selectedEmbeddedSubtitle = null
        subtitleMode = SubtitleMode.EXTERNAL
        showBanner(
            Icons.Rounded.ClosedCaption,
            "Subtitle file",
            subtitleLabel ?: "File eksternal",
        )
    }

    fun removeExternalSubtitle() {
        subtitleUri = null
        subtitleLabel = null
        subtitleCues = emptyList()
        selectedEmbeddedSubtitle = null
        subtitleMode = if (embeddedSubtitles.isNotEmpty()) SubtitleMode.AUTO else SubtitleMode.OFF
        showBanner(
            Icons.Rounded.ClosedCaption,
            "Subtitle file dilepas",
            if (subtitleMode == SubtitleMode.AUTO) {
                "Kembali ke trek bawaan video"
            } else {
                "File eksternal tidak lagi ditampilkan"
            },
        )
    }

    fun toggleDnd() {
        when {
            dndActive -> {
                dnd.release()
                dndActive = false
                showBanner(Icons.Rounded.DoNotDisturbOn, "Jangan ganggu: mati", "Notifikasi kembali normal")
            }
            dnd.hasAccess() -> {
                dndActive = dnd.activate()
                dndGranted = dndActive
                showBanner(
                    Icons.Rounded.DoNotDisturbOn,
                    if (dndActive) "Cinema DND aktif" else "Jangan ganggu gagal aktif",
                    "Notifikasi melayang ditahan selama menonton",
                )
            }
            else -> {
                dndPending = true
                showBanner(
                    Icons.Rounded.DoNotDisturbOn,
                    "Jangan ganggu",
                    "Izinkan akses kebijakan notifikasi di pengaturan",
                )
                dnd.requestAccess()
            }
        }
    }

    fun leave() {
        viewModel.onStop(exoPlayer.currentPosition)
        onBack()
    }

    fun toggleLock() {
        locked = !locked
        controlsVisible = false
        panelTab = null
        unlockVisible = false
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        subtitleUri = uri
    }

    BackHandler {
        when {
            locked -> locked = false
            panelTab != null -> panelTab = null
            else -> leave()
        }
    }

    LaunchedEffect(bannerToken) {
        if (banner != null) {
            delay(1_500)
            banner = null
        }
    }

    LaunchedEffect(unlockToken) {
        if (unlockVisible) {
            delay(2_500)
            unlockVisible = false
        }
    }

    // Preferences persist so the next episode keeps the chosen look and sound.
    LaunchedEffect(resizeMode) { preferences.resizeMode = resizeMode }
    LaunchedEffect(exoPlayer, speed) {
        preferences.speed = speed
        // A decoder switch builds a new player, which starts at 1x.
        exoPlayer.setPlaybackSpeed(speed)
    }
    LaunchedEffect(subtitleSize) { preferences.subtitleSize = subtitleSize }
    LaunchedEffect(subtitleSyncMs) { preferences.subtitleSyncMs = subtitleSyncMs }
    LaunchedEffect(eqPreset, boostPercent) {
        preferences.loudnessBoost = boostPercent
        audioEffects.apply(eqPreset, boostPercent)
    }
    LaunchedEffect(backgroundPlay) { preferences.backgroundPlay = backgroundPlay }

    // Rebuild the media item when the episode changes or the decoder was
    // re-created; a decoder switch keeps the exact position.
    LaunchedEffect(exoPlayer, state.uri) {
        val videoUri = state.uri ?: return@LaunchedEffect
        val sameItem = loadedUri == videoUri && exoPlayer.mediaItemCount > 0
        val resume = when {
            sameItem -> exoPlayer.currentPosition
            pendingResumeMs >= 0L -> pendingResumeMs
            else -> state.resumeMs
        }
        pendingResumeMs = -1L
        playerError = null
        ended = false
        buffering = true
        autoNextCancelled = false
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        if (resume > 0L) exoPlayer.seekTo(resume)
        // Switching decoder while paused must not start the video again.
        exoPlayer.playWhenReady = pendingPlayWhenReady ?: true
        pendingPlayWhenReady = null
        loadedUri = videoUri
    }

    // Embedded tracks are rendered by Media3; external files are rendered by
    // this screen so the sync stepper can shift their timing. Auto lets the
    // player pick the default embedded track.
    LaunchedEffect(exoPlayer, subtitleMode) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (subtitleMode == SubtitleMode.AUTO) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        }
        exoPlayer.trackSelectionParameters = builder
            .setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                subtitleMode == SubtitleMode.OFF || subtitleMode == SubtitleMode.EXTERNAL,
            )
            .build()
    }

    LaunchedEffect(subtitleUri) {
        val uri = subtitleUri
        if (uri == null) {
            subtitleCues = emptyList()
            subtitleLabel = null
            return@LaunchedEffect
        }
        subtitleLoading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val name = DocumentFile.fromSingleUri(context, uri)?.name
                    ?: uri.lastPathSegment
                    ?: "subtitle"
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("File subtitle tidak bisa dibaca")
                name to parseSubtitleContent(name, text)
            }
        }
        subtitleLoading = false
        result.onSuccess { (name, cues) ->
            if (cues.isEmpty()) {
                subtitleUri = null
                subtitleCues = emptyList()
                subtitleMode = if (embeddedSubtitles.isNotEmpty()) {
                    SubtitleMode.AUTO
                } else {
                    SubtitleMode.OFF
                }
                showBanner(Icons.Rounded.ClosedCaption, "Subtitle kosong", "Format tidak dikenali atau tidak ada teks")
            } else {
                subtitleLabel = name
                subtitleCues = cues
                selectedEmbeddedSubtitle = null
                subtitleMode = SubtitleMode.EXTERNAL
                showBanner(Icons.Rounded.ClosedCaption, "Subtitle dimuat", "$name • ${cues.size} baris")
            }
        }.onFailure { error ->
            subtitleUri = null
            subtitleCues = emptyList()
            subtitleMode = if (embeddedSubtitles.isNotEmpty()) SubtitleMode.AUTO else SubtitleMode.OFF
            showBanner(
                Icons.Rounded.ClosedCaption,
                "Subtitle gagal dimuat",
                error.message ?: "Coba file lain",
            )
        }
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
            // Subtitles need a finer clock; without them a slower poll keeps
            // the whole screen from recomposing ten times a second.
            delay(if (subtitleCues.isNotEmpty()) 100 else 300)
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

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffects.attach(audioSessionId)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width.toFloat() / videoSize.height
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val options = mutableListOf<AudioOption>()
                val heights = mutableListOf<Int>()
                val subtitles = mutableListOf<SubtitleTrackOption>()
                var defaultSubtitle: SubtitleTrackOption? = null
                tracks.groups.forEach { group ->
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO -> {
                            for (index in 0 until group.length) {
                                val format = group.getTrackFormat(index)
                                val label = format.label
                                    ?: format.language?.uppercase()
                                    ?: "Track ${index + 1}"
                                options += AudioOption(group.mediaTrackGroup, index, label)
                            }
                        }
                        C.TRACK_TYPE_VIDEO -> {
                            for (index in 0 until group.length) {
                                val height = group.getTrackFormat(index).height
                                if (height > 0) heights += height
                            }
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (index in 0 until group.length) {
                                val format = group.getTrackFormat(index)
                                val base = format.label?.takeIf { it.isNotBlank() }
                                    ?: subtitleLanguageName(format.language)
                                    ?: "Subtitle ${index + 1}"
                                val option = SubtitleTrackOption(
                                    group = group.mediaTrackGroup,
                                    trackIndex = index,
                                    label = "$base [Bawaan]",
                                    id = format.id,
                                )
                                subtitles += option
                                if (group.isTrackSelected(index)) defaultSubtitle = option
                            }
                        }
                    }
                }
                audioOptions = options
                videoHeights = heights.distinct().sortedDescending()
                embeddedSubtitles = subtitles
                if (subtitleMode == SubtitleMode.AUTO && defaultSubtitle != null) {
                    selectedEmbeddedSubtitle = defaultSubtitle
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            viewModel.onStop(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioEffects.release()
            PlaybackEngine.release(context)
            PlayerPip.inPip = false
        }
    }

    SideEffect {
        PlayerPip.aspectRatio = videoAspect
    }

    // Full-screen playback: hide the system bars and show them again on exit.
    DisposableEffect(view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val attributes = window.attributes
            attributes.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attributes
        }
    }

    // Fullscreen forces landscape; exiting hands control back to the sensor.
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(activity, fullscreen) {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

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
        viewModel.onStop(exoPlayer.currentPosition)
        // Background play keeps audio running; without it the video pauses as
        // soon as the app leaves the foreground.
        if (!backgroundPlay) exoPlayer.pause()
        dnd.release()
        dndActive = false
    }

    // Sleep timer: Off -> 15 -> 30 -> 60 minutes.
    LaunchedEffect(sleepOption) {
        if (sleepOption == SleepOption.OFF) return@LaunchedEffect
        delay(sleepOption.minutes * 60_000L)
        exoPlayer.pause()
        sleepOption = SleepOption.OFF
        showBanner(Icons.Rounded.Bedtime, "Sleep timer", "Waktu habis, pemutaran dijeda")
    }

    LaunchedEffect(controlsVisible, isPlaying, scrubbing, locked, panelTab, seekPreview) {
        if (controlsVisible && isPlaying && !scrubbing && !locked && panelTab == null && seekPreview == null) {
            delay(HUD_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(ended, state.index) {
        autoNextCancelled = false
        autoNextCountdown = -1
        if (ended && state.hasNext) {
            for (second in AUTO_NEXT_SECONDS downTo 1) {
                if (autoNextCancelled) break
                autoNextCountdown = second
                delay(1_000)
            }
            // Without background play, finishing while the app is in the
            // background must not start the next episode's audio silently.
            val canAdvance = backgroundPlay || PlayerPip.inPip ||
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!autoNextCancelled && ended && state.hasNext && canAdvance) {
                autoNextCountdown = -1
                openEntry(state.index + 1)
            }
        }
    }

    LaunchedEffect(panelTab) {
        panelTab?.let { lastPanelTab = it }
    }

    val currentCue = activeTextCue(subtitleCues, positionMs, subtitleSyncMs)

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
                        setKeepScreenOn(true)
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = resizeMode
                },
            )
        }

        if (!PlayerPip.inPip) {
            // Transparent gesture area. Buttons are drawn above this layer, so
            // drags start on the video while taps on controls still hit them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(locked, panelTab, exoPlayer, state.uri) {
                        if (locked || panelTab != null || state.uri == null) return@pointerInput
                        var axis = DragAxis.NONE
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var startPosition = 0L
                        var startBrightness = 0f
                        var startVolume = 0f
                        var startX = 0f
                        var targetPosition = 0L
                        detectDragGestures(
                            onDragStart = { offset ->
                                axis = DragAxis.NONE
                                accumulatedX = 0f
                                accumulatedY = 0f
                                startX = offset.x
                                startPosition = exoPlayer.currentPosition
                                startBrightness = brightness
                                startVolume = volumeFraction()
                                targetPosition = startPosition
                            },
                            onDragEnd = {
                                if (axis == DragAxis.SEEK) seekTo(targetPosition)
                                axis = DragAxis.NONE
                                levelOverlay = null
                                seekPreview = null
                            },
                            onDragCancel = {
                                axis = DragAxis.NONE
                                levelOverlay = null
                                seekPreview = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedX += dragAmount.x
                                accumulatedY += dragAmount.y
                                if (axis == DragAxis.NONE) {
                                    val threshold = 14.dp.toPx()
                                    if (abs(accumulatedX) > threshold || abs(accumulatedY) > threshold) {
                                        axis = if (abs(accumulatedX) > abs(accumulatedY)) {
                                            DragAxis.SEEK
                                        } else if (startX < size.width / 2f) {
                                            DragAxis.BRIGHTNESS
                                        } else {
                                            DragAxis.VOLUME
                                        }
                                    }
                                }
                                when (axis) {
                                    DragAxis.SEEK -> {
                                        val delta = (accumulatedX / size.width * SEEK_SWIPE_MS).toLong()
                                        val duration = exoPlayer.duration.coerceAtLeast(0L)
                                        targetPosition = (startPosition + delta)
                                            .coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
                                        seekPreview = SeekPreview(targetPosition, targetPosition - startPosition)
                                        controlsVisible = true
                                    }
                                    DragAxis.BRIGHTNESS -> {
                                        setBrightness(startBrightness - accumulatedY / size.height)
                                        levelOverlay = LevelOverlay(LevelKind.BRIGHTNESS, brightness)
                                    }
                                    DragAxis.VOLUME -> {
                                        val value = (startVolume - accumulatedY / size.height).coerceIn(0f, 1f)
                                        setVolume(value)
                                        levelOverlay = LevelOverlay(LevelKind.VOLUME, value)
                                    }
                                    DragAxis.NONE -> Unit
                                }
                            },
                        )
                    }
                    .pointerInput(locked, panelTab) {
                        if (locked || panelTab != null) return@pointerInput
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2f
                                seekTo(exoPlayer.currentPosition + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
                                showBanner(
                                    if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                                    if (forward) "Maju 10 detik" else "Mundur 10 detik",
                                    formatTime(positionMs),
                                )
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

            currentCue?.let { cue ->
                Text(
                    text = cue.text,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = (16 * subtitleSize).sp,
                        fontWeight = FontWeight.Medium,
                        shadow = Shadow(Color.Black, blurRadius = 8f),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = if (controlsVisible) 116.dp else 44.dp,
                        ),
                )
            }

            if (subtitleLoading) {
                Text(
                    text = "Memuat subtitle…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Zone 1: top bar, at most three icons on the right.
            if (controlsVisible && state.uri != null && !ended && !locked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali dari pemutar",
                        onClick = ::leave,
                        size = 38.dp,
                        iconSize = 20.dp,
                    )
                    Spacer(Modifier.width(8.dp))
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
                                    append("Ep ${state.index + 1}/${state.entries.size}")
                                }
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    BadgedIconButton(
                        icon = Icons.Rounded.Memory,
                        contentDescription = "Ganti dekoder (${decoderMode.badge})",
                        badge = decoderMode.badge,
                        active = decoderMode != DecoderMode.AUTO,
                        onClick = ::cycleDecoder,
                    )
                    BadgedIconButton(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = "Buka playlist episode",
                        badge = state.entries.size.toString(),
                        active = panelTab == PanelTab.EPISODES,
                        onClick = { panelTab = PanelTab.EPISODES },
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = "Kecilkan ke Picture-in-Picture",
                        onClick = { PlayerPip.enter(activity) },
                        size = 38.dp,
                        iconSize = 19.dp,
                    )
                }
            }

            // Zone 2: centre transport.
            if (controlsVisible && state.uri != null && !ended && !locked) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.Replay10,
                        contentDescription = "Mundur 10 detik",
                        onClick = { seekTo(exoPlayer.currentPosition - SEEK_STEP_MS) },
                        size = 44.dp,
                        iconSize = 24.dp,
                    )
                    GlassIconButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else {
                                if (ended) replay() else exoPlayer.play()
                            }
                        },
                        tint = BrandMint,
                        size = 58.dp,
                        iconSize = 30.dp,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.Forward10,
                        contentDescription = "Maju 10 detik",
                        onClick = { seekTo(exoPlayer.currentPosition + SEEK_STEP_MS) },
                        size = 44.dp,
                        iconSize = 24.dp,
                    )
                }
            }

            seekPreview?.let { preview ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatTime(preview.targetMs),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatDelta(preview.deltaMs),
                        color = BrandMint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            levelOverlay?.let { overlay ->
                val icon = if (overlay.kind == LevelKind.BRIGHTNESS) {
                    Icons.Rounded.Brightness6
                } else {
                    Icons.AutoMirrored.Rounded.VolumeUp
                }
                Row(
                    modifier = Modifier
                        .align(
                            if (overlay.kind == LevelKind.VOLUME) Alignment.CenterStart else Alignment.CenterEnd,
                        )
                        .padding(horizontal = 18.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(overlay.value * 100).roundToInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // Zone 3: bottom bar.
            if (controlsVisible && state.uri != null && !ended && !locked) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        contentDescription = "Posisi pemutaran video",
                        stateDescription = "${formatTime(if (scrubbing) scrubPosition else positionMs)} " +
                            "dari ${formatTime(durationMs)}",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(
                            icon = Icons.Rounded.Lock,
                            contentDescription = "Kunci layar",
                            onClick = ::toggleLock,
                            size = 36.dp,
                            iconSize = 18.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${formatTime(if (scrubbing) scrubPosition else positionMs)} / " +
                                formatTime(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        // No embedded tracks and no external file: the CC icon
                        // has nothing to control. The tab stays reachable from
                        // the panel for loading a file later.
                        if (embeddedSubtitles.isNotEmpty() || subtitleUri != null) {
                            BadgedIconButton(
                                icon = Icons.Rounded.ClosedCaption,
                                contentDescription = "Buka pengaturan subtitle",
                                badge = "CC",
                                active = subtitleMode != SubtitleMode.OFF,
                                onClick = { panelTab = PanelTab.SUBTITLE },
                            )
                        }
                        BadgedIconButton(
                            icon = Icons.Rounded.GraphicEq,
                            contentDescription = "Audio dan equalizer",
                            badge = eqPreset.badge,
                            active = eqPreset != EqPreset.NORMAL || boostPercent > 100,
                            onClick = { panelTab = PanelTab.AUDIO },
                        )
                        BadgedIconButton(
                            icon = Icons.Rounded.AspectRatio,
                            contentDescription = "Ganti rasio layar (${resizeLabel(resizeMode)})",
                            badge = resizeLabel(resizeMode),
                            active = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            onClick = ::cycleAspect,
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Buka setelan pemutar",
                            onClick = { panelTab = PanelTab.SETTINGS },
                            size = 38.dp,
                            iconSize = 19.dp,
                        )
                        Spacer(Modifier.width(2.dp))
                        GlassIconButton(
                            icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (fullscreen) "Keluar layar penuh" else "Layar penuh landscape",
                            onClick = {
                                fullscreen = !fullscreen
                                showBanner(
                                    if (fullscreen) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                                    if (fullscreen) "Layar penuh" else "Ikuti rotasi perangkat",
                                    if (fullscreen) "Landscape dan HUD disembunyikan otomatis" else "Orientasi kembali ke sensor",
                                )
                            },
                            size = 38.dp,
                            iconSize = 19.dp,
                        )
                    }
                }
            }

            // Rich action banner, 1.5s.
            banner?.let { current ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 66.dp, start = 16.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(current.icon, contentDescription = null, tint = BrandMint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = current.title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = current.message,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // End of episode / auto-next countdown.
            if (ended && playerError == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.hasNext) "Episode selesai" else "Semua episode selesai",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.hasNext && autoNextCountdown > 0) {
                        Text(
                            text = "Episode berikutnya dalam ${autoNextCountdown} detik",
                            color = BrandMint,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Putar ulang",
                            icon = Icons.Rounded.Replay,
                            onClick = ::replay,
                            height = 38.dp,
                        )
                        if (state.hasNext) {
                            GlassButton(
                                text = "Putar sekarang",
                                icon = Icons.Rounded.SkipNext,
                                onClick = { openEntry(state.index + 1) },
                                tint = BrandMint,
                                height = 38.dp,
                            )
                            GlassButton(
                                text = "Batal",
                                onClick = {
                                    autoNextCancelled = true
                                    autoNextCountdown = -1
                                },
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

            // Smart screen lock: only the unlock capsule reacts while locked.
            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                unlockVisible = true
                                unlockToken++
                            }
                        },
                )
                if (unlockVisible) {
                    GlassButton(
                        text = "Buka kunci",
                        icon = Icons.Rounded.LockOpen,
                        onClick = {
                            locked = false
                            unlockVisible = false
                            controlsVisible = true
                        },
                        tint = BrandMint,
                        height = 44.dp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Zone 4: 285dp panel with four tabs, sliding in from the right
            // in landscape and from the bottom in portrait.
            val openTab = panelTab
            AnimatedVisibility(
                visible = openTab != null,
                enter = if (isLandscape) {
                    slideInHorizontally { it } + fadeIn()
                } else {
                    slideInVertically { it / 2 } + fadeIn()
                },
                exit = if (isLandscape) {
                    slideOutHorizontally { it } + fadeOut()
                } else {
                    slideOutVertically { it / 2 } + fadeOut()
                },
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { panelTab = null },
                )
                PlayerSidePanel(
                    tab = openTab ?: lastPanelTab,
                    onTab = { panelTab = it },
                    onClose = { panelTab = null },
                    isLandscape = isLandscape,
                ) {
                        when (openTab ?: lastPanelTab) {
                            PanelTab.EPISODES -> EpisodesTab(
                                state = state,
                                onOpen = ::openEntry,
                            )
                            PanelTab.SUBTITLE -> SubtitleTab(
                                embedded = embeddedSubtitles,
                                selectedEmbedded = selectedEmbeddedSubtitle,
                                mode = subtitleMode,
                                externalLabel = subtitleLabel,
                                cuesCount = subtitleCues.size,
                                subtitleSize = subtitleSize,
                                syncMs = subtitleSyncMs,
                                onSelectEmbedded = ::selectEmbeddedSubtitle,
                                onDisable = ::disableSubtitles,
                                onUseExternal = ::useExternalSubtitle,
                                onPickFile = {
                                    runCatching { subtitlePicker.launch(arrayOf("*/*")) }
                                        .onFailure {
                                            showBanner(
                                                Icons.Rounded.ClosedCaption,
                                                "Pemilih file gagal",
                                                "Coba lagi dari pengaturan subtitle",
                                            )
                                        }
                                },
                                onRemove = ::removeExternalSubtitle,
                                onSize = {
                                    subtitleSize = it
                                    showBanner(
                                        Icons.Rounded.ClosedCaption,
                                        "Ukuran subtitle",
                                        when (it) {
                                            0.85f -> "Kecil (S)"
                                            1.3f -> "Besar (L)"
                                            else -> "Normal (M)"
                                        },
                                    )
                                },
                                onSync = { delta ->
                                    subtitleSyncMs = (subtitleSyncMs + delta)
                                        .coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS)
                                },
                                onSyncReset = { subtitleSyncMs = 0L },
                            )
                            PanelTab.AUDIO -> AudioTab(
                                options = audioOptions,
                                selected = selectedAudio,
                                onSelect = ::selectAudio,
                                preset = eqPreset,
                                onPreset = ::setEq,
                                boost = boostPercent,
                                onBoost = { boostPercent = it },
                            )
                            PanelTab.SETTINGS -> SettingsTab(
                                speed = speed,
                                onSpeed = {
                                    speed = it
                                    showBanner(
                                        Icons.Rounded.Speed,
                                        "Kecepatan ${speedLabel(it)}",
                                        "Aturan ini dipakai lagi di episode berikutnya",
                                    )
                                },
                                heights = videoHeights,
                                qualityHeight = qualityHeight,
                                onQuality = ::setQuality,
                                backgroundPlay = backgroundPlay,
                                onMessage = { message ->
                                    showBanner(Icons.Rounded.BatterySaver, "Optimasi sistem", message)
                                },
                                onBackgroundPlay = {
                                    backgroundPlay = !backgroundPlay
                                    showBanner(
                                        Icons.Rounded.Headphones,
                                        if (backgroundPlay) "Background play aktif" else "Background play mati",
                                        if (backgroundPlay) {
                                            "Audio tetap berbunyi saat aplikasi ke latar belakang"
                                        } else {
                                            "Video dijeda saat aplikasi ditinggalkan"
                                        },
                                    )
                                },
                                sleepOption = sleepOption,
                                onSleep = {
                                    sleepOption = sleepOption.next()
                                    if (sleepOption != SleepOption.OFF) {
                                        showBanner(
                                            Icons.Rounded.Bedtime,
                                            "Sleep timer: ${sleepOption.label}",
                                            "Pemutaran dijeda otomatis saat waktu habis",
                                        )
                                    } else {
                                        showBanner(Icons.Rounded.Timer, "Sleep timer mati", "Tidak ada jeda otomatis")
                                    }
                                },
                                dndActive = dndActive,
                                onDnd = ::toggleDnd,
                            )
                        }
                }
            }
            }
        }
    }
}

/** Side panel in landscape, bottom sheet in portrait; both expose the same tabs. */
@Composable
private fun BoxScope.PlayerSidePanel(
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    onClose: () -> Unit,
    isLandscape: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (isLandscape) {
        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    }
    Surface(
        modifier = if (isLandscape) {
            Modifier
                .fillMaxHeight()
                .width(PanelWidth)
                .align(Alignment.CenterEnd)
                .shadow(24.dp, shape)
                .pointerInput(Unit) { detectTapGestures { } }
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.BottomCenter)
                .shadow(24.dp, shape)
                .pointerInput(Unit) { detectTapGestures { } }
        },
        color = Color(0xF210151C),
        contentColor = TextPrimary,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (tab) {
                        PanelTab.EPISODES -> "Daftar episode"
                        PanelTab.SUBTITLE -> "Subtitle"
                        PanelTab.AUDIO -> "Audio & EQ"
                        PanelTab.SETTINGS -> "Setelan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Tutup panel",
                    onClick = onClose,
                    size = 34.dp,
                    iconSize = 18.dp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PanelTab.entries.forEach { item ->
                    val selected = item == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) BrandMint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
                            )
                            .clickable { onTab(item) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (selected) BrandMint else TextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = if (selected) BrandMint else TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ColumnScope.EpisodesTab(
    state: PlayerUiState,
    onOpen: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassButton(
            text = "Sebelum",
            icon = Icons.Rounded.SkipPrevious,
            enabled = state.hasPrevious,
            onClick = { onOpen(state.index - 1) },
            height = 36.dp,
            modifier = Modifier.weight(1f),
        )
        GlassButton(
            text = "Berikut",
            icon = Icons.Rounded.SkipNext,
            enabled = state.hasNext,
            onClick = { onOpen(state.index + 1) },
            height = 36.dp,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = "${state.entries.size} episode • ${state.folderLabel}",
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(state.entries, key = { _, entry -> entry.fileName }) { index, entry ->
            val current = index == state.index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (current) BrandMint else TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(28.dp),
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
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SubtitleTab(
    embedded: List<SubtitleTrackOption>,
    selectedEmbedded: SubtitleTrackOption?,
    mode: SubtitleMode,
    externalLabel: String?,
    cuesCount: Int,
    subtitleSize: Float,
    syncMs: Long,
    onSelectEmbedded: (SubtitleTrackOption) -> Unit,
    onDisable: () -> Unit,
    onUseExternal: () -> Unit,
    onPickFile: () -> Unit,
    onRemove: () -> Unit,
    onSize: (Float) -> Unit,
    onSync: (Long) -> Unit,
    onSyncReset: () -> Unit,
) {
    val hasExternal = externalLabel != null && cuesCount > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Trek subtitle", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        if (embedded.isEmpty() && !hasExternal) {
            Text(
                text = "Tidak ada trek subtitle di video ini. Muat file SRT atau WebVTT " +
                    "dari penyimpanan untuk menampilkan teks.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SubtitleChoiceRow(
            title = "Mati",
            subtitle = "Tanpa teks subtitle",
            selected = mode == SubtitleMode.OFF,
            onClick = onDisable,
        )
        embedded.forEach { option ->
            SubtitleChoiceRow(
                title = option.label,
                subtitle = "Trek bawaan video",
                selected = (mode == SubtitleMode.EMBEDDED || mode == SubtitleMode.AUTO) &&
                    sameSubtitleTrack(selectedEmbedded, option),
                onClick = { onSelectEmbedded(option) },
            )
        }
        if (hasExternal) {
            SubtitleChoiceRow(
                title = externalLabel ?: "File subtitle",
                subtitle = "$cuesCount baris • File eksternal",
                selected = mode == SubtitleMode.EXTERNAL,
                onClick = onUseExternal,
            )
        }
        GlassButton(
            text = if (hasExternal) {
                "Ganti file subtitle"
            } else {
                "Muat file subtitle (.srt/.vtt/ass)"
            },
            icon = Icons.Rounded.FolderOpen,
            onClick = onPickFile,
            height = 42.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasExternal) {
            GlassButton(
                text = "Lepas file subtitle",
                icon = Icons.Rounded.Close,
                onClick = onRemove,
                height = 38.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (mode == SubtitleMode.EXTERNAL) {
            Spacer(Modifier.height(2.dp))
            Text("Ukuran teks", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleSizes.forEach { (value, label) ->
                    GridChip(
                        text = label,
                        selected = subtitleSize == value,
                        onClick = { onSize(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Sinkronisasi subtitle",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassButton(
                    text = "−0.1s",
                    onClick = { onSync(-100L) },
                    height = 36.dp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatSync(syncMs),
                    color = BrandMint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 58.dp),
                )
                GlassButton(
                    text = "+0.1s",
                    onClick = { onSync(100L) },
                    height = 36.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            GlassButton(
                text = "Reset sinkronisasi",
                onClick = onSyncReset,
                height = 36.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Format yang dibaca: SRT, WebVTT, SSA/ASS. Sinkronisasi menggeser waktu " +
                    "teks tanpa memuat ulang video; ukuran dan offset dipakai lagi di episode " +
                    "berikutnya.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        } else if (embedded.isNotEmpty()) {
            Text(
                text = "Subtitle bawaan dirender Media3 dengan label bahasanya. Ukuran dan " +
                    "sinkronisasi hanya tersedia untuk file subtitle eksternal.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SubtitleChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandMint.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) BrandMint else TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = BrandMint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.AudioTab(
    options: List<AudioOption>,
    selected: AudioOption?,
    onSelect: (AudioOption?) -> Unit,
    preset: EqPreset,
    onPreset: (EqPreset) -> Unit,
    boost: Int,
    onBoost: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Track audio", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        AudioRow(
            title = "Otomatis",
            subtitle = "Ikuti track utama file",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        options.forEach { option ->
            AudioRow(
                title = option.label,
                subtitle = "Track ${option.trackIndex + 1}",
                selected = selected?.let { it.label == option.label && it.trackIndex == option.trackIndex } == true,
                onClick = { onSelect(option) },
            )
        }
        if (options.isEmpty()) {
            Text(
                text = "File ini belum memuat track audio yang terbaca.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Preset equalizer", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        EqPreset.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (preset == item) BrandMint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                            )
                            .border(
                                1.dp,
                                if (preset == item) BrandMint.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onPreset(item) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = item.label,
                            color = if (preset == item) BrandMint else TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.badge,
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Loudness boost",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$boost%",
                color = if (boost > 100) BrandMint else TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = boost.toFloat(),
            onValueChange = { onBoost(it.roundToInt()) },
            valueRange = 100f..150f,
            colors = SliderDefaults.colors(
                thumbColor = BrandMint,
                activeTrackColor = BrandMint,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
        )
        Text(
            text = "Hingga +150% untuk dialog pelan. Boost di atas 100% menambah gain " +
                "audio dan bisa memotong suara keras; preset EQ diterapkan lewat audio session ExoPlayer.",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AudioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandMint.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) BrandMint else TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = BrandMint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsTab(
    speed: Float,
    onSpeed: (Float) -> Unit,
    heights: List<Int>,
    qualityHeight: Int,
    onQuality: (Int) -> Unit,
    backgroundPlay: Boolean,
    onMessage: (String) -> Unit,
    onBackgroundPlay: () -> Unit,
    sleepOption: SleepOption,
    onSleep: () -> Unit,
    dndActive: Boolean,
    onDnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Kecepatan", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        PlaybackSpeeds.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    GridChip(
                        text = speedLabel(value),
                        selected = speed == value,
                        onClick = { onSpeed(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Kualitas video", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        val qualityOptions = listOf(0) + heights
        qualityOptions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { height ->
                    GridChip(
                        text = if (height == 0) "Auto" else "${height}p",
                        selected = qualityHeight == height,
                        onClick = { onQuality(height) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (heights.size <= 1) {
            Text(
                text = "File lokal umumnya hanya punya satu resolusi; pilihan mengikuti track " +
                    "yang tersedia.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        UtilityCard(
            icon = Icons.Rounded.Headphones,
            title = "Background play",
            value = if (backgroundPlay) "Audio tetap berbunyi" else "Video dijeda saat keluar",
            active = backgroundPlay,
            onClick = onBackgroundPlay,
        )
        if (backgroundPlay) {
            SystemOptimizationHint(
                title = "Izin latar untuk background play",
                reason = "Agar audio tetap berbunyi saat layar mati atau aplikasi " +
                    "ditinggalkan tanpa ditunda penghemat baterai.",
                onMessage = onMessage,
            )
        }
        UtilityCard(
            icon = Icons.Rounded.Bedtime,
            title = "Sleep timer",
            value = if (sleepOption == SleepOption.OFF) "Mati" else "Jeda otomatis • ${sleepOption.label}",
            active = sleepOption != SleepOption.OFF,
            onClick = onSleep,
        )
        UtilityCard(
            icon = Icons.Rounded.DoNotDisturbOn,
            title = "Cinema DND",
            value = if (dndActive) "Notifikasi melayang ditahan" else "Notifikasi normal",
            active = dndActive,
            onClick = onDnd,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Background play berjalan lewat layanan media foreground dengan kontrol " +
                "putar/jeda di notifikasi dan jendela PiP. Pada perangkat dengan penghemat " +
                "agresif, izin baterai di atas tetap diperlukan.",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun UtilityCard(
    icon: ImageVector,
    title: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) BrandMint.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (active) BrandMint.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) BrandMint else TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(text = value, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = if (active) "AKTIF" else "OFF",
            color = if (active) BrandMint else TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GridChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) BrandMint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (selected) BrandMint.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) BrandMint else TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 38dp icon with a tiny 3-4 character status badge underneath. */
@Composable
private fun BadgedIconButton(
    icon: ImageVector,
    contentDescription: String,
    badge: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            tint = if (active) BrandMint else Color.White,
            size = 36.dp,
            iconSize = 18.dp,
        )
        Text(
            text = badge,
            color = if (active) Color(0xFF04120F) else Color.White.copy(alpha = 0.86f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (active) BrandMint else Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 3.dp, vertical = 0.5.dp),
        )
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

private fun formatDelta(ms: Long): String {
    val sign = if (ms >= 0) "+" else "−"
    val seconds = abs(ms) / 1000
    return "%s%02d:%02d".format(sign, seconds / 60, seconds % 60)
}

private fun subtitleLanguageName(tag: String?): String? {
    if (tag.isNullOrBlank() || tag.equals("und", ignoreCase = true)) return null
    val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return null
    val name = locale.getDisplayLanguage(Locale("id")).takeIf { it.isNotBlank() } ?: return null
    return if (name.equals("Indonesia", ignoreCase = true)) "Bahasa Indonesia" else name
}

private fun sameSubtitleTrack(
    selected: SubtitleTrackOption?,
    option: SubtitleTrackOption,
): Boolean {
    if (selected == null) return false
    return selected.trackIndex == option.trackIndex &&
        selected.id == option.id &&
        selected.label == option.label
}

private fun formatSync(ms: Long): String {
    val sign = if (ms > 0) "+" else if (ms < 0) "−" else ""
    val absMs = abs(ms)
    return "%s%.1fs".format(sign, absMs / 1000f)
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun resizeLabel(mode: Int): String =
    ResizeModes.firstOrNull { it.first == mode }?.second ?: "FIT"

private fun nextResizeMode(current: Int): Int {
    val index = ResizeModes.indexOfFirst { it.first == current }
    return ResizeModes[(index + 1).coerceAtLeast(0) % ResizeModes.size].first
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    else -> (this as? ContextWrapper)?.baseContext?.findActivity()
}
