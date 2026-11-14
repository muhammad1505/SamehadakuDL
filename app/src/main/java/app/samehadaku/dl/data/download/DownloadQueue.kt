package app.samehadaku.dl.data.download

import app.samehadaku.dl.core.SiteMirrors
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.network.NeedsVerificationException
import app.samehadaku.dl.data.parser.HostSelector
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.data.subscriptions.SubscriptionRepository
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import app.samehadaku.dl.domain.repository.SiteRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class DownloadQueue @Inject constructor(
    private val downloader: Downloader,
    private val siteRepository: SiteRepository,
    private val settings: SettingsRepository,
    private val queueStore: QueueStore,
    private val subscriptions: SubscriptionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val schedulerMutex = Mutex()
    private val persistMutex = Mutex()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    @Volatile
    private var concurrency: Int = 1

    init {
        scope.launch {
            val restored = runCatching { queueStore.load() }.getOrDefault(emptyList())
            if (restored.isNotEmpty()) {
                var restoredActive = false
                val normalized = restored.map { task ->
                    if (task.state.isActive) {
                        restoredActive = true
                        task.copy(
                            state = DownloadState.PAUSED,
                            speedBytesPerSecond = 0L,
                            currentHost = null,
                        )
                    } else {
                        task
                    }
                }
                _tasks.value = normalized
                if (restoredActive) {
                    runCatching { queueStore.save(normalized) }
                }
            }
            ready.complete(Unit)
            schedule()
        }

        scope.launch {
            settings.concurrency.collect { value ->
                concurrency = value
                schedule()
            }
        }
    }

    suspend fun enqueue(newTasks: List<DownloadTask>) {
        if (newTasks.isEmpty()) return
        ready.await()
        _tasks.update { it + newTasks }
        persist()
        schedule()
    }

    fun pauseAll() {
        scope.launch {
            ready.await()
            val jobsToCancel = mutableListOf<Job>()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.state.isActive) {
                        activeJobs[task.id]?.let { jobsToCancel.add(it) }
                        task.copy(
                            state = DownloadState.PAUSED,
                            speedBytesPerSecond = 0L,
                            currentHost = null,
                        )
                    } else {
                        task
                    }
                }
            }
            jobsToCancel.forEach { it.cancel() }
            persist()
        }
    }

    fun resumeAll() {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.state == DownloadState.PAUSED) {
                        task.copy(
                            state = DownloadState.QUEUED,
                            failureReasons = emptyList(),
                            note = null,
                            mirrorSource = null,
                            finishedAt = null,
                        )
                    } else {
                        task
                    }
                }
            }
            persist()
            schedule()
        }
    }

    fun cancelAll() {
        scope.launch {
            ready.await()
            val jobsToCancel = mutableListOf<Job>()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.state.isActive || task.state == DownloadState.PAUSED) {
                        activeJobs[task.id]?.let { jobsToCancel.add(it) }
                        task.copy(
                            state = DownloadState.CANCELED,
                            speedBytesPerSecond = 0L,
                            finishedAt = System.currentTimeMillis(),
                        )
                    } else {
                        task
                    }
                }
            }
            jobsToCancel.forEach { it.cancel() }
            persist()
        }
    }

    fun cancel(id: String) {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.id == id && !task.state.isFinished) {
                        task.copy(
                            state = DownloadState.CANCELED,
                            speedBytesPerSecond = 0L,
                            finishedAt = System.currentTimeMillis(),
                        )
                    } else {
                        task
                    }
                }
            }
            activeJobs[id]?.cancel()
            persist()
        }
    }

    fun pause(id: String) {
        scope.launch {
            ready.await()
            val target = _tasks.value.firstOrNull { it.id == id } ?: return@launch
            if (!target.state.isActive) return@launch
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.id == id && task.state.isActive) {
                        task.copy(
                            state = DownloadState.PAUSED,
                            speedBytesPerSecond = 0L,
                            currentHost = null,
                        )
                    } else {
                        task
                    }
                }
            }
            activeJobs[id]?.cancel()
            persist()
        }
    }

    fun resume(id: String) {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.id == id && task.state == DownloadState.PAUSED) {
                        task.copy(
                            state = DownloadState.QUEUED,
                            failureReasons = emptyList(),
                            note = null,
                            mirrorSource = null,
                            finishedAt = null,
                        )
                    } else {
                        task
                    }
                }
            }
            persist()
            schedule()
        }
    }

    fun remove(id: String) {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.filterNot { task -> task.id == id && task.state.isFinished }
            }
            persist()
        }
    }

    fun retry(id: String) {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.id == id && (task.state.isFinished || task.state == DownloadState.PAUSED)) {
                        task.copy(
                            state = DownloadState.QUEUED,
                            bytesDone = 0L,
                            totalBytes = 0L,
                            speedBytesPerSecond = 0L,
                            currentHost = null,
                            failureReasons = emptyList(),
                            note = null,
                            mirrorSource = null,
                            finishedAt = null,
                            retryCount = 0,
                        )
                    } else {
                        task
                    }
                }
            }
            persist()
            schedule()
        }
    }

    fun retryFailed() {
        scope.launch {
            ready.await()
            _tasks.update { tasks ->
                tasks.map { task ->
                    if (task.state == DownloadState.FAILED || task.state == DownloadState.SKIPPED) {
                        task.copy(
                            state = DownloadState.QUEUED,
                            bytesDone = 0L,
                            totalBytes = 0L,
                            speedBytesPerSecond = 0L,
                            currentHost = null,
                            failureReasons = emptyList(),
                            note = null,
                            mirrorSource = null,
                            finishedAt = null,
                            retryCount = 0,
                        )
                    } else {
                        task
                    }
                }
            }
            persist()
            schedule()
        }
    }

    /**
     * Re-queues failed or skipped tasks whose last attempt is older than [minAgeMillis],
     * at most [maxAttempts] automatic retries per task. Dead host links often reappear
     * when the uploader refreshes them, so the episode page is worth another look.
     */
    suspend fun retryFailedDue(minAgeMillis: Long, maxAttempts: Int): Int {
        ready.await()
        val now = System.currentTimeMillis()
        val due = _tasks.value
            .filter { task ->
                (task.state == DownloadState.FAILED || task.state == DownloadState.SKIPPED) &&
                    task.retryCount < maxAttempts &&
                    now - (task.finishedAt ?: task.createdAt) >= minAgeMillis
            }
            .map { it.id }
            .toSet()
        if (due.isEmpty()) return 0
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.id in due) {
                    task.copy(
                        state = DownloadState.QUEUED,
                        bytesDone = 0L,
                        totalBytes = 0L,
                        speedBytesPerSecond = 0L,
                        currentHost = null,
                        failureReasons = emptyList(),
                        note = null,
                        mirrorSource = null,
                        finishedAt = null,
                        retryCount = task.retryCount + 1,
                    )
                } else {
                    task
                }
            }
        }
        persist()
        schedule()
        return due.size
    }

    fun clearFinished() {
        scope.launch {
            ready.await()
            _tasks.update { tasks -> tasks.filterNot { it.state.isFinished } }
            persist()
        }
    }

    private fun schedule() {
        scope.launch {
            ready.await()
            schedulerMutex.withLock {
                while (activeJobs.size < concurrency) {
                    val next = _tasks.value.firstOrNull { it.state == DownloadState.QUEUED } ?: break
                    startTask(next)
                }
            }
        }
    }

    private fun startTask(task: DownloadTask) {
        val current = _tasks.value.firstOrNull { it.id == task.id } ?: return
        if (current.state != DownloadState.QUEUED) return

        _tasks.update { tasks ->
            tasks.map {
                if (it.id == task.id) {
                    it.copy(
                        state = DownloadState.RESOLVING,
                        bytesDone = 0L,
                        totalBytes = 0L,
                        speedBytesPerSecond = 0L,
                        currentHost = null,
                        failureReasons = emptyList(),
                        note = null,
                        mirrorSource = null,
                    )
                } else {
                    it
                }
            }
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            runTask(task.id)
        }
        activeJobs[task.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(task.id)
            schedule()
        }
        job.start()
    }

    private suspend fun runTask(taskId: String) {
        val initial = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (!initial.state.isActive) return

        Timber.i(
            "Start episode %d (%s): requested %s %s via %s",
            initial.episodeNumber,
            initial.episodeUrl,
            initial.format.label,
            initial.resolution,
            initial.preferredHost.label,
        )

        try {
            val links = siteRepository.loadDownloadLinks(initial.episodeUrl)
            if (links == null || links.isEmpty) {
                finish(
                    taskId = taskId,
                    state = DownloadState.SKIPPED,
                    reasons = listOf("Tautan unduhan tidak ditemukan atau episode belum rilis"),
                )
                return
            }

            // loadDownloadLinks already falls back to a mirror when the requested
            // page is unreachable; keep that source for the queue report. Any host
            // change is reported, not only a move from a primary to a mirror.
            val requestedHost = UrlUtils.hostOf(initial.episodeUrl)
            var mirrorSource = links.sourceUrl
                ?.let(UrlUtils::hostOf)
                ?.takeIf { served -> !served.equals(requestedHost, ignoreCase = true) }
            Timber.i(
                "Episode %d links from %s (requested host %s)",
                initial.episodeNumber,
                links.sourceUrl ?: initial.episodeUrl,
                requestedHost ?: "unknown",
            )

            val allowFormatFallback = runCatching { settings.currentQualityDowngrade() }
                .getOrDefault(false)
            fun candidatesFor(
                linkSet: DownloadLinks,
                fallbackFormat: Boolean,
            ): List<HostSelector.Selection> = HostSelector.findCandidates(
                links = linkSet,
                format = initial.format,
                resolution = initial.resolution,
                preferredHost = initial.preferredHost,
            ).filter { candidate -> candidate.adjusted == fallbackFormat }

            suspend fun attempt(
                linkSet: DownloadLinks,
                candidates: List<HostSelector.Selection>,
            ): DownloadOutcome {
                if (candidates.isEmpty()) return DownloadOutcome.Skipped("Kualitas tidak tersedia")
                return downloader.download(initial, linkSet, { progress ->
                    updateProgress(taskId, progress)
                }, candidates)
            }

            val reasons = mutableListOf<String>()
            fun remember(outcome: DownloadOutcome) {
                when (outcome) {
                    is DownloadOutcome.Failed -> reasons += outcome.reasons
                    is DownloadOutcome.Skipped -> reasons += outcome.reason
                    is DownloadOutcome.Done -> Unit
                }
            }

            var outcome = attempt(links, candidatesFor(links, fallbackFormat = false))
            remember(outcome)
            Timber.i(
                "Episode %d exact quality attempt: %s",
                initial.episodeNumber,
                outcome.logLabel(),
            )

            val mirrorSets = if (outcome is DownloadOutcome.Done) {
                emptyList()
            } else {
                runCatching { siteRepository.loadMirrorLinks(initial.episodeUrl) }
                    .getOrDefault(emptyList())
            }
            Timber.i("Episode %d mirrors to try: %d", initial.episodeNumber, mirrorSets.size)

            // Mirrors are checked for the exact requested quality before any format
            // fallback. A mirror may still have X265 480p even when the main domain
            // only has MKV/MP4, and changing format too early was the reported bug.
            for (mirrorLinks in mirrorSets) {
                if (outcome is DownloadOutcome.Done) break
                if (mirrorLinks.data == links.data) continue
                Timber.i(
                    "Episode %d try exact quality on mirror %s",
                    initial.episodeNumber,
                    mirrorLinks.sourceUrl ?: "unknown",
                )
                val mirrorOutcome = attempt(
                    mirrorLinks,
                    candidatesFor(mirrorLinks, fallbackFormat = false),
                )
                remember(mirrorOutcome)
                Timber.i(
                    "Episode %d exact mirror result: %s",
                    initial.episodeNumber,
                    mirrorOutcome.logLabel(),
                )
                if (mirrorOutcome is DownloadOutcome.Done) {
                    outcome = mirrorOutcome
                    val servedHost = mirrorLinks.sourceUrl?.let(UrlUtils::hostOf)
                    mirrorSource = when {
                        servedHost == null -> "Samehadaku"
                        servedHost.equals(requestedHost, ignoreCase = true) -> null
                        else -> servedHost
                    }
                }
            }

            if (outcome !is DownloadOutcome.Done && allowFormatFallback) {
                Timber.i("Episode %d trying same-resolution format fallback", initial.episodeNumber)
                val fallback = attempt(links, candidatesFor(links, fallbackFormat = true))
                remember(fallback)
                if (fallback is DownloadOutcome.Done) {
                    outcome = fallback
                }
                for (mirrorLinks in mirrorSets) {
                    if (outcome is DownloadOutcome.Done) break
                    if (mirrorLinks.data == links.data) continue
                    val mirrorFallback = attempt(
                        mirrorLinks,
                        candidatesFor(mirrorLinks, fallbackFormat = true),
                    )
                    remember(mirrorFallback)
                    if (mirrorFallback is DownloadOutcome.Done) {
                        outcome = mirrorFallback
                        val servedHost = mirrorLinks.sourceUrl?.let(UrlUtils::hostOf)
                        mirrorSource = when {
                            servedHost == null -> "Samehadaku"
                            servedHost.equals(requestedHost, ignoreCase = true) -> null
                            else -> servedHost
                        }
                    }
                }
            }

            if (outcome !is DownloadOutcome.Done && reasons.isNotEmpty()) {
                outcome = DownloadOutcome.Failed(reasons.distinct())
            }

            when (outcome) {
                is DownloadOutcome.Done -> finish(
                    taskId = taskId,
                    state = DownloadState.DONE,
                    note = buildList {
                        if (outcome.adjusted) {
                            add(
                                "Kualitas disesuaikan otomatis ke " +
                                    "${outcome.format.label} ${outcome.resolution} karena " +
                                    "pilihan awal tidak tersedia di episode ini.",
                            )
                        }
                        if (outcome.host != initial.preferredHost) {
                            add(
                                "Diunduh lewat ${outcome.host.label} karena " +
                                    "${initial.preferredHost.label} tidak tersedia untuk " +
                                    "kualitas ini.",
                            )
                        }
                        if (outcome.alreadyExisted) {
                            add("File sudah ada di penyimpanan, unduhan tidak diulang.")
                        }
                    }.joinToString(" ").ifBlank { null },
                    fileName = outcome.fileName,
                    alreadyExisted = outcome.alreadyExisted,
                    format = outcome.format,
                    resolution = outcome.resolution,
                    host = outcome.host,
                    mirrorSource = mirrorSource,
                )
                is DownloadOutcome.Failed -> finish(
                    taskId = taskId,
                    state = DownloadState.FAILED,
                    reasons = outcome.reasons,
                )
                is DownloadOutcome.Skipped -> finish(
                    taskId = taskId,
                    state = DownloadState.SKIPPED,
                    reasons = listOf(outcome.reason),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (verification: NeedsVerificationException) {
            finish(
                taskId = taskId,
                state = DownloadState.FAILED,
                reasons = listOf(
                    "Perlu verifikasi Cloudflare. Buka halaman utama lalu tekan Verifikasi, " +
                        "kemudian coba lagi.",
                ),
            )
        } catch (error: Exception) {
            Timber.w(error, "Download task failed: %s", taskId)
            finish(
                taskId = taskId,
                state = DownloadState.FAILED,
                reasons = listOf("Terjadi kesalahan: ${error.message ?: "tidak diketahui"}"),
            )
        }
    }

    private fun DownloadOutcome.logLabel(): String = when (this) {
        is DownloadOutcome.Done ->
            "done ${format.label} $resolution via ${host.label}" +
                if (adjusted) " (fallback)" else ""
        is DownloadOutcome.Failed -> "failed: ${reasons.joinToString("; ")}"
        is DownloadOutcome.Skipped -> "skipped: $reason"
    }

    private fun updateProgress(taskId: String, progress: DownloadProgress) {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId && task.state.isActive) {
                    task.copy(
                        state = DownloadState.DOWNLOADING,
                        bytesDone = progress.bytesDone,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSecond = progress.speedBytesPerSecond,
                        currentHost = progress.host,
                        fileName = progress.fileName,
                    )
                } else {
                    task
                }
            }
        }
    }

    private fun finish(
        taskId: String,
        state: DownloadState,
        reasons: List<String> = emptyList(),
        note: String? = null,
        fileName: String? = null,
        alreadyExisted: Boolean = false,
        format: VideoFormat? = null,
        resolution: String? = null,
        host: Host? = null,
        mirrorSource: String? = null,
    ) {
        var finishedTask: DownloadTask? = null
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId && task.state.isActive) {
                    finishedTask = task
                    task.copy(
                        state = state,
                        format = format ?: task.format,
                        resolution = resolution ?: task.resolution,
                        speedBytesPerSecond = 0L,
                        // Keep the actual server in history instead of erasing it when
                        // the task finishes; failures keep the last attempted host.
                        currentHost = host ?: task.currentHost,
                        failureReasons = reasons,
                        note = note,
                        mirrorSource = mirrorSource,
                        fileName = fileName ?: task.fileName,
                        alreadyExisted = alreadyExisted,
                        finishedAt = System.currentTimeMillis(),
                        bytesDone = if (state == DownloadState.DONE) {
                            maxOf(task.bytesDone, task.totalBytes)
                        } else {
                            task.bytesDone
                        },
                    )
                } else {
                    task
                }
            }
        }
        persist()
        val completed = finishedTask
        if (state == DownloadState.DONE && completed != null) {
            // Keep the "last episode" of a followed anime in sync with real downloads.
            scope.launch {
                runCatching {
                    subscriptions.recordDownloadedEpisode(
                        slug = completed.animeSlug,
                        episodeNumber = completed.episodeNumber,
                    )
                }
            }
        }
    }

    private fun persist() {
        scope.launch {
            persistMutex.withLock {
                runCatching { queueStore.save(_tasks.value) }
            }
        }
    }
}
