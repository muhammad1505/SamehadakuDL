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
import app.samehadaku.dl.core.Names
import app.samehadaku.dl.data.notifications.NotificationRepository
import app.samehadaku.dl.domain.model.AppNotificationKind
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var finishedPosted = false
    private var stopping = false

    /**
     * Tasks that belong to this service run. The queue deliberately keeps finished
     * tasks as history, so without this list every new notification would also count
     * downloads from previous sessions.
     */
    private val sessionTaskIds = mutableSetOf<String>()
    private val sessionStartedAt = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopping = false
        when (intent?.action) {
            ACTION_PAUSE_ALL -> queue.pauseAll()
            ACTION_RESUME_ALL -> queue.resumeAll()
            ACTION_CANCEL_ALL -> queue.cancelAll()
        }

        // Reuse the current state so notification actions do not flash a blank
        // "preparing" notification while the queue is updating.
        val current = queue.tasks.value
        rememberSessionTasks(current)
        val relevant = sessionTasks(current)
        startAsForeground(
            when {
                relevant.any { it.state.isActive } -> buildProgressNotification(relevant)
                relevant.any { it.state == DownloadState.PAUSED } ->
                    buildPausedNotification(relevant)

                else -> emptyNotification()
            },
        )

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
        scope.cancel()
        super.onDestroy()
    }

    private fun handleTasks(tasks: List<DownloadTask>) {
        if (stopping) return

        if (tasks.isEmpty()) {
            stopForegroundAndSelf()
            return
        }

        rememberSessionTasks(tasks)
        val relevant = sessionTasks(tasks)
        val active = relevant.any { it.state.isActive }
        val paused = relevant.any { it.state == DownloadState.PAUSED }

        if (active) {
            finishedPosted = false
            updateNotification(buildProgressNotification(relevant))
        } else if (paused) {
            finishedPosted = false
            updateNotification(buildPausedNotification(relevant))
        } else if (!finishedPosted) {
            finishedPosted = true
            finishSession(relevant)
        }
    }

    /**
     * Adds every task that takes part in this run. Active and paused tasks always
     * belong to it; finished tasks only when they were queued just before the
     * service started, which covers episodes skipped by the link preflight.
     */
    private fun rememberSessionTasks(tasks: List<DownloadTask>) {
        tasks.forEach { task ->
            val queuedForThisRun =
                (task.finishedAt ?: 0L) >= sessionStartedAt - PREFLIGHT_GRACE_MS
            if (!task.state.isFinished || queuedForThisRun) {
                sessionTaskIds += task.id
            }
        }
    }

    private fun sessionTasks(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.filter { it.id in sessionTaskIds }

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

    /**
     * Keeps the same notification id from progress to paused to finished. The
     * finished notification is detached from the foreground service instead of
     * removed, so the session ends as one continuous notification.
     */
    private fun finishSession(tasks: List<DownloadTask>) {
        val finished = tasks.filter { it.state.isFinished }
            .sortedWith(
                compareBy<DownloadTask> {
                    it.state == DownloadState.DONE || it.state == DownloadState.CANCELED
                }.thenBy { it.finishedAt ?: it.createdAt },
            )
        if (finished.isEmpty()) {
            stopForegroundAndSelf()
            return
        }

        val done = finished.count { it.state == DownloadState.DONE }
        val failed = finished.count {
            it.state == DownloadState.FAILED || it.state == DownloadState.SKIPPED
        }
        val canceled = finished.count { it.state == DownloadState.CANCELED }
        val duplicates = finished.count { it.state == DownloadState.DONE && it.alreadyExisted }

        val title = when {
            failed == 0 && canceled == 0 -> "Unduhan selesai"
            failed == 0 -> "Unduhan dihentikan"
            else -> "Unduhan selesai dengan masalah"
        }
        val summary = buildString {
            append("$done berhasil")
            append(" • $failed gagal/dilewati")
            if (canceled > 0) append(" • $canceled dibatalkan")
            if (duplicates > 0) append(" • $duplicates sudah ada")
        }
        val blocks = finished.take(MAX_TASK_BLOCKS).map { it.notificationBlock() }
        val remaining = finished.size - blocks.size
        val bigText = buildString {
            append(summary)
            if (blocks.isNotEmpty()) {
                append('\n')
                append(blocks.joinToString("\n"))
            }
            if (remaining > 0) append("\n+$remaining episode lainnya")
        }
        val focus = finished.firstOrNull()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(focus?.let { "${it.episodeLine()} • ${it.metaLine()}" } ?: summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        updateNotification(notification)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopping = true
        scope.launch {
            runCatching {
                inbox.add(
                    kind = AppNotificationKind.COMPLETED,
                    title = title,
                    message = bigText,
                    route = MainActivity.ROUTE_QUEUE,
                )
            }
            stopSelf()
        }
    }

    private fun buildProgressNotification(tasks: List<DownloadTask>): Notification {
        val activeTasks = tasks.filter { it.state.isActive }
        val done = tasks.count { it.state == DownloadState.DONE }
        val totalBytes = tasks.sumOf { it.totalBytes.coerceAtLeast(0L) }
        val doneBytes = tasks.sumOf { it.bytesDone.coerceAtLeast(0L) }
        val speed = activeTasks.sumOf { it.speedBytesPerSecond.coerceAtLeast(0L) }
        val percent = if (totalBytes > 0L) {
            ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val summary = buildString {
            append("$done selesai")
            if (totalBytes > 0L) {
                append(" • ${Formatter.formatShortFileSize(this@DownloadService, doneBytes)}")
                append(" / ${Formatter.formatShortFileSize(this@DownloadService, totalBytes)}")
            }
            if (speed > 0L) {
                append(" • ${Formatter.formatShortFileSize(this@DownloadService, speed)}/dtk")
            }
        }
        val blocks = activeTasks.take(MAX_TASK_BLOCKS).map { it.notificationBlock() }
        val remaining = activeTasks.size - blocks.size
        val bigText = buildString {
            append(summary)
            if (blocks.isNotEmpty()) {
                append('\n')
                append(blocks.joinToString("\n"))
            }
            if (remaining > 0) append("\n+$remaining episode lainnya")
        }
        val focus = activeTasks.firstOrNull()
        val title = if (activeTasks.size == 1 && focus != null) {
            Names.fromSlug(focus.animeSlug)
        } else {
            "Mengunduh ${activeTasks.size} episode"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(focus?.let { "${it.episodeLine()} • ${it.metaLine()}" } ?: summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
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
        val paused = tasks.filter { it.state == DownloadState.PAUSED }
        val summary = "${paused.size} episode menunggu dilanjutkan."
        val blocks = paused.take(MAX_TASK_BLOCKS).map { it.notificationBlock() }
        val remaining = paused.size - blocks.size
        val bigText = buildString {
            append(summary)
            if (blocks.isNotEmpty()) {
                append('\n')
                append(blocks.joinToString("\n"))
            }
            if (remaining > 0) append("\n+$remaining episode lainnya")
        }
        val focus = paused.firstOrNull()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Unduhan dijeda")
            .setContentText(focus?.let { "${it.episodeLine()} • ${it.metaLine()}" } ?: summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Lanjut semua", serviceAction(ACTION_RESUME_ALL, REQUEST_RESUME))
            .addAction(0, "Batalkan semua", serviceAction(ACTION_CANCEL_ALL, REQUEST_CANCEL))
            .build()
    }

    /**
     * Two lines per episode: the anime title with its episode number, then the data
     * that actually matters during and after a download. Using the same builder for
     * progress, paused, and finished keeps every state equally complete.
     */
    private fun DownloadTask.notificationBlock(): String =
        "${titleLine()}\n${metaLine()}"

    private fun DownloadTask.titleLine(): String =
        "${Names.fromSlug(animeSlug)} • ${episodeLine()}"

    private fun DownloadTask.episodeLine(): String = "Episode $episodeNumber"

    private fun DownloadTask.metaLine(): String = buildString {
        append("${format.label} ${resolution.uppercase()}")
        append(" • ${(currentHost ?: preferredHost).label}")
        if (totalBytes > 0L) {
            append(" • ")
            append(Formatter.formatShortFileSize(this@DownloadService, totalBytes))
        }
        when (state) {
            DownloadState.QUEUED -> append(" • menunggu")
            DownloadState.RESOLVING -> append(" • menyiapkan tautan")
            DownloadState.DOWNLOADING -> {
                if (totalBytes > 0L) {
                    append(" • ")
                    append((progress * 100).roundToInt())
                    append('%')
                }
                if (speedBytesPerSecond > 0L) {
                    append(" • ")
                    append(
                        Formatter.formatShortFileSize(
                            this@DownloadService,
                            speedBytesPerSecond,
                        ),
                    )
                    append("/dtk")
                }
            }

            DownloadState.PAUSED -> append(" • dijeda")
            DownloadState.DONE -> append(if (alreadyExisted) " • sudah ada" else " • selesai")
            DownloadState.FAILED -> {
                append(" • gagal")
                append(reasonSuffix())
            }

            DownloadState.SKIPPED -> {
                append(" • dilewati")
                append(reasonSuffix())
            }

            DownloadState.CANCELED -> append(" • dibatalkan")
        }
    }

    private fun DownloadTask.reasonSuffix(): String {
        val reason = (failureReasons.firstOrNull() ?: note)
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
            ?: return ""
        val short = if (reason.length <= MAX_REASON_CHARS) {
            reason
        } else {
            reason.take(MAX_REASON_CHARS - 1) + "…"
        }
        return ": $short"
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
        private const val REQUEST_OPEN = 3001
        private const val REQUEST_PAUSE = 3002
        private const val REQUEST_CANCEL = 3003
        private const val REQUEST_RESUME = 3004
        private const val MAX_TASK_BLOCKS = 4
        private const val MAX_REASON_CHARS = 120
        private const val PREFLIGHT_GRACE_MS = 15_000L
        private const val ACTION_PAUSE_ALL = "app.samehadaku.dl.action.PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "app.samehadaku.dl.action.RESUME_ALL"
        private const val ACTION_CANCEL_ALL = "app.samehadaku.dl.action.CANCEL_ALL"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
