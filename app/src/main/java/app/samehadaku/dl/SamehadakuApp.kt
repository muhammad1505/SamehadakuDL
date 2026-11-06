package app.samehadaku.dl

import android.app.Application
import android.os.Build
import android.webkit.WebView
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.data.network.CloudflareSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class SamehadakuApp : Application() {

    @Inject
    lateinit var cloudflareSession: CloudflareSession

    @Inject
    lateinit var autoCheckScheduler: AutoCheckScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            WebView.setWebContentsDebuggingEnabled(true)
        }
        appScope.launch {
            runCatching { cloudflareSession.restore() }
            // Re-register the periodic check after reboots, updates or a settings change.
            runCatching { autoCheckScheduler.sync() }
            // Some OEMs freeze WorkManager; catch up when the user opens the app.
            runCatching { autoCheckScheduler.runNowIfDue() }
        }
    }
}
