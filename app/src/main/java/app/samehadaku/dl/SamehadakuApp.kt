package app.samehadaku.dl

import android.app.Application
import android.os.Build
import android.webkit.WebView
import app.samehadaku.dl.core.AppLog
import app.samehadaku.dl.data.autocheck.AutoCheckScheduler
import app.samehadaku.dl.data.network.CloudflareSession
import app.samehadaku.dl.data.smartstorage.SmartStorageScheduler
import app.samehadaku.dl.domain.usecase.AutoRetryFailedUseCase
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

    @Inject
    lateinit var autoRetryFailed: AutoRetryFailedUseCase

    @Inject
    lateinit var smartStorageScheduler: SmartStorageScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.installCrashHandler()
        Timber.plant(AppLog.Tree())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            WebView.setWebContentsDebuggingEnabled(true)
        }
        appScope.launch {
            runCatching { cloudflareSession.restore() }
            // Failed links are often re-uploaded later; retry them on every app start.
            runCatching { autoRetryFailed() }
            // Re-register the periodic check after reboots, updates or a settings change.
            runCatching { autoCheckScheduler.sync() }
            // Smart Storage cleanup is separate from the network check: it only
            // touches files the user opted in to remove.
            runCatching { smartStorageScheduler.sync() }
            // Some OEMs freeze WorkManager; catch up when the user opens the app.
            runCatching { autoCheckScheduler.runNowIfDue() }
        }
    }
}
