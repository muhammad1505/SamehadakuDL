package app.samehadaku.dl.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.samehadaku.dl.MainActivity
import app.samehadaku.dl.R
import app.samehadaku.dl.data.notifications.NotificationRepository
import app.samehadaku.dl.domain.model.AppNotificationKind
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var queue: DownloadQueue

    @Inject
    lateinit var inbox: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var stopJob: Job? = null
    private var summaryPosted = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> queue.pauseAll()
            ACTION_CANCEL_ALL -> queue.cancelAll()
        }

        NotificationManagerCompat.from(this).cancel(SUMMARY_NOTIFICATION_ID)
        startAsForeground(emptyNotification())

        if (collectJob == null) {
            collectJob = scope.launch {
                queue.tasks.collectLatest { tasks -> handleTasks(tasks) }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectJob?.cancel()
        stopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun handleTasks(tasks: List<DownloadTask>) {
        if (stopping) return

        if (tasks.isEmpty()) {
            stopForegroundAndSelf()
            return
        }

        val active = tasks.any { it.state.isActive }
        val paused = tasks.any { it.state == DownloadState.PAUSED }

        if (active) {
            stopJob?.cancel()
            stopJob = null
            summaryPosted = false
            updateNotification(buildProgressNotification(tasks))
        } else if (paused) {
            stopJob?.cancel()
            stopJob = null
            updateNotification(buildPausedNotification(tasks))
        } else if (!summaryPosted) {
            summaryPosted = true
            postSummary(tasks)
            stopJob = scope.launch {
                delay(SUMMARY_DISPLAY_MS)
                stopForegroundAndSelf()
            }
        }
    }

    private fun startAsForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        } catch (error: Exception) {
            Timber.w(error, "startForeground failed")
            stopSelf()
        }
    }

    private fun updateNotification(notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Timber.w(error, "Notification permission denied")
        }
    }

    private fun postSummary(tasks: List<DownloadTask>) {
        val done = tasks.count { it.state == DownloadState.DONE }
        val failed = tasks.count {
            it.state == DownloadState.FAILED || it.state == DownloadState.SKIPPED
        }
        val canceled = tasks.count { it.state == DownloadState.CANCELED }
        val duplicates = tasks.count { it.state == DownloadState.DONE && it.alreadyExisted }

        val title = when {
            failed == 0 && canceled == 0 -> "Unduhan selesai"
            failed == 0 -> "Unduhan dihentikan"
            else -> "Unduhan selesai dengan masalah"
        }
        val text = buildString {
            append("$done berhasil")
            append(" • $failed gagal/dilewati")
            if (canceled > 0) append(" • $canceled dibatalkan")
            if (duplicates > 0) append(" • $duplicates sudah ada, dilewati")
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(SUMMARY_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Timber.w(error, "Notification permission denied")
        }
        scope.launch {
            runCatching {
                inbox.add(
                    kind = AppNotificationKind.COMPLETED,
                    title = title,
                    message = text,
                    route = MainActivity.ROUTE_QUEUE,
                )
            }
        }
    }

    private fun buildProgressNotification(tasks: List<DownloadTask>): Notification {
        val activeTasks = tasks.filter { it.state.isActive }
        val done = tasks.count { it.state == DownloadState.DONE }
        val totalBytes = tasks.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        val doneBytes = tasks.sumOf { it.bytesDone }
        val speed = activeTasks.sumOf { it.speedBytesPerSecond }
        val percent = if (totalBytes > 0L) {
            ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val detail = buildString {
            append("$done selesai")
            if (totalBytes > 0L) {
                append(" • ${Formatter.formatShortFileSize(this@DownloadService, doneBytes)}")
                append(" / ${Formatter.formatShortFileSize(this@DownloadService, totalBytes)}")
            }
            if (speed > 0L) {
                append(" • ${Formatter.formatShortFileSize(this@DownloadService, speed)}/dtk")
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Mengunduh ${activeTasks.size} episode")
            .setContentText(detail)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (totalBytes > 0L) {
                    setProgress(100, percent, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .addAction(0, "Jeda semua", serviceAction(ACTION_PAUSE_ALL, REQUEST_PAUSE))
            .addAction(0, "Batalkan semua", serviceAction(ACTION_CANCEL_ALL, REQUEST_CANCEL))
            .build()
    }

    private fun buildPausedNotification(tasks: List<DownloadTask>): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Unduhan dijeda")
            .setContentText("${tasks.count { it.state == DownloadState.PAUSED }} episode menunggu. Buka aplikasi untuk melanjutkan.")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Batalkan semua", serviceAction(ACTION_CANCEL_ALL, REQUEST_CANCEL))
            .build()
    }

    private fun emptyNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Menyiapkan unduhan…")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Always land on the download queue, not whichever tab was open last.
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, MainActivity.ROUTE_QUEUE)
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundAndSelf() {
        if (stopping) return
        stopping = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2001
        private const val SUMMARY_NOTIFICATION_ID = 2002
        private const val REQUEST_OPEN = 3001
        private const val REQUEST_PAUSE = 3002
        private const val REQUEST_CANCEL = 3003
        private const val SUMMARY_DISPLAY_MS = 4_000L
        private const val ACTION_PAUSE_ALL = "app.samehadaku.dl.action.PAUSE_ALL"
        private const val ACTION_CANCEL_ALL = "app.samehadaku.dl.action.CANCEL_ALL"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
