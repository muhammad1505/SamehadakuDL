package app.samehadaku.dl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.presentation.navigation.SamehadakuNavHost
import app.samehadaku.dl.presentation.splash.SplashScreen
import app.samehadaku.dl.presentation.theme.SamehadakuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var autoCheckScheduler: AutoCheckScheduler

    private var sharedUrl by mutableStateOf<String?>(null)
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrl = extractSharedUrl(intent)
        pendingRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SamehadakuTheme {
                // WorkManager runs are inexact and OEMs may freeze them; catch up when
                // the app becomes visible so new episodes show up without a manual check.
                val checkScope = rememberCoroutineScope()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    checkScope.launch {
                        runCatching { autoCheckScheduler.runNowIfDue() }
                    }
                }
                var showSplash by rememberSaveable { mutableStateOf(true) }
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
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractSharedUrl(intent)
        pendingRoute = intent.getStringExtra(EXTRA_OPEN_ROUTE)
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

        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
