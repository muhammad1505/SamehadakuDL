package app.samehadaku.dl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.data.smartstorage.SmartStorageManager
import app.samehadaku.dl.data.smartstorage.SmartStorageScheduler
import app.samehadaku.dl.presentation.about.UpdateChangelogDialog
import app.samehadaku.dl.presentation.about.readReleaseNotes
import app.samehadaku.dl.presentation.navigation.SamehadakuNavHost
import app.samehadaku.dl.presentation.player.PlayerPip
import app.samehadaku.dl.presentation.splash.SplashScreen
import app.samehadaku.dl.presentation.theme.SamehadakuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var autoCheckScheduler: AutoCheckScheduler

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var smartStorageManager: SmartStorageManager

    @Inject
    lateinit var smartStorageScheduler: SmartStorageScheduler

    private var sharedUrl by mutableStateOf<String?>(null)
    private var pendingRoute by mutableStateOf<String?>(null)
    private var externalVideoUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrl = extractSharedUrl(intent)
        pendingRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        externalVideoUri = extractVideoUri(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SamehadakuTheme {
                // WorkManager runs are inexact and OEMs may freeze them; catch up when
                // the app becomes visible so new episodes show up without a manual check.
                val checkScope = rememberCoroutineScope()
                val updateScope = rememberCoroutineScope()
                val storageScope = rememberCoroutineScope()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    checkScope.launch {
                        runCatching { autoCheckScheduler.runNowIfDue() }
                    }
                    // The external player takes the app to the background. On the
                    // way back, credit that sitting to the recorded playback and
                    // catch up on cleanup if WorkManager was frozen.
                    storageScope.launch {
                        runCatching { smartStorageManager.evaluatePendingPlayback() }
                        runCatching { smartStorageScheduler.runNowIfDue() }
                    }
                }
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var showUpdateChangelog by rememberSaveable { mutableStateOf(false) }
                var updateNotes by remember { mutableStateOf(emptyList<app.samehadaku.dl.presentation.about.ReleaseNote>()) }
                LaunchedEffect(showSplash) {
                    if (!showSplash && settingsRepository.shouldShowChangelog(BuildConfig.VERSION_NAME)) {
                        updateNotes = withContext(Dispatchers.IO) { readReleaseNotes(this@MainActivity) }
                        showUpdateChangelog = true
                    }
                }
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 260),
                    label = "splash-to-app",
                ) { splashVisible ->
                    if (splashVisible) {
                        SplashScreen(
                            onFinished = {
                                showSplash = false
                                requestNotificationPermissionIfNeeded()
                            },
                        )
                    } else {
                        SamehadakuNavHost(
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                            pendingRoute = pendingRoute,
                            onPendingRouteConsumed = { pendingRoute = null },
                            externalVideoUri = externalVideoUri,
                            onExternalVideoConsumed = { externalVideoUri = null },
                        )
                    }
                }
                if (showUpdateChangelog) {
                    UpdateChangelogDialog(
                        notes = updateNotes,
                        onDismiss = {
                            showUpdateChangelog = false
                            updateScope.launch {
                                settingsRepository.markChangelogSeen(BuildConfig.VERSION_NAME)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractSharedUrl(intent)
        pendingRoute = intent.getStringExtra(EXTRA_OPEN_ROUTE)
        externalVideoUri = extractVideoUri(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPip.inPip = isInPictureInPictureMode
    }

    /** Extracts the first link from text shared through Android's share sheet. */
    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isBlank()) return null
        val match = Regex("https?://\\S+").find(text)?.value ?: return null
        return match.trimEnd('.', ',', ';', ')', ']', '}')
    }

    /**
     * Video opened from a file manager or another app via "Buka dengan".
     *
     * The read grant is persisted when the sender allows it so playback survives a
     * configuration change; sources that only grant a temporary read still play
     * for this session because the activity keeps the intent grant.
     */
    private fun extractVideoUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme.isNullOrBlank()) return null
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return uri.toString()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST,
                )
            }
        }
    }

    companion object {
        /** Route opened when the app is launched from a notification. */
        const val EXTRA_OPEN_ROUTE = "app.samehadaku.dl.extra.OPEN_ROUTE"
        const val ROUTE_SUBSCRIPTIONS = "subscriptions"
        const val ROUTE_CLOUDFLARE = "cloudflare"
        const val ROUTE_QUEUE = "queue"
        const val ROUTE_SMART_STORAGE = "smart-storage"

        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
