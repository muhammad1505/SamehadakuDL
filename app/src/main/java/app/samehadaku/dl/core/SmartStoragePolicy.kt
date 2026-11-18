package app.samehadaku.dl.core

/**
 * Pure rules for Smart Storage. Keeping them here makes the risky parts (when an
 * episode counts as watched and when a file may be deleted) testable without
 * Android, a DataStore or a real file.
 */
object SmartStoragePolicy {

    /** Grace periods offered in Settings; the first value is the default. */
    val graceDayOptions = listOf(1, 3, 7, 14)
    const val defaultGraceDays = 3

    /**
     * A playback is treated as finished when the accumulated time spent outside
     * the app while the video was open covers at least 80% of the duration. One
     * sitting longer than 300% of the duration is discarded as unreliable (the
     * player was probably left open or paused), so it can never become a marker.
     */
    const val minWatchRatio = 0.8
    const val maxWatchRatio = 3.0

    private const val dayMillis = 24L * 60L * 60L * 1000L

    fun normalizeGraceDays(days: Int): Int {
        val candidate = days.coerceAtLeast(1)
        return graceDayOptions.minByOrNull { option ->
            kotlin.math.abs(option - candidate)
        } ?: defaultGraceDays
    }

    /** Whether the grace period since the watched marker has fully elapsed. */
    fun isDue(watchedAt: Long, now: Long, graceDays: Int): Boolean {
        if (watchedAt <= 0L || now < watchedAt) return false
        val graceMillis = normalizeGraceDays(graceDays).toLong() * dayMillis
        return now - watchedAt >= graceMillis
    }

    /**
     * Automatic cleanup waits for the grace period, but an explicit user action
     * may delete a watched episode immediately. Invalid or future markers stay
     * protected even when the user asks for immediate cleanup.
     */
    fun shouldDelete(
        watchedAt: Long,
        now: Long,
        graceDays: Int,
        ignoreGrace: Boolean = false,
    ): Boolean {
        if (watchedAt <= 0L || now < watchedAt) return false
        return ignoreGrace || isDue(watchedAt, now, graceDays)
    }

    /**
     * Time from one sitting that may be credited. A sitting far longer than the
     * video is more likely a paused or abandoned player than real watching, so
     * it counts as zero instead of becoming a false marker.
     */
    fun usableSessionMs(sessionMs: Long, durationMs: Long): Long {
        if (sessionMs <= 0L || durationMs <= 0L) return 0L
        val ceiling = (durationMs * maxWatchRatio).toLong()
        return if (sessionMs > ceiling) 0L else sessionMs
    }

    /** Accumulated sitting time, not one wall-clock span, decides a marker. */
    fun countsAsWatched(watchedMs: Long, durationMs: Long): Boolean {
        if (watchedMs <= 0L || durationMs <= 0L) return false
        return watchedMs >= durationMs * minWatchRatio
    }

    /** Timestamp at which a watched marker becomes eligible for cleanup. */
    fun dueAt(watchedAt: Long, graceDays: Int): Long {
        if (watchedAt <= 0L) return 0L
        return watchedAt + normalizeGraceDays(graceDays).toLong() * dayMillis
    }

    /**
     * A recorded size is required so a file can be compared byte-for-byte before
     * it is deleted. Unknown or zero-sized files stay watched but are never
     * removed automatically.
     */
    fun hasUsableSize(recordedSize: Long): Boolean = recordedSize > 0L

    /** Safe folder name used by both the download writer and the watched store. */
    fun normalizeFolder(folderName: String): String =
        UrlUtils.fileNameSafe(folderName).ifBlank { "SamehadakuDL" }

    /** Stable identity for one downloaded file; used by the UI and the store. */
    fun key(folderName: String, fileName: String): String =
        "${normalizeFolder(folderName)}\u0000$fileName"

    /**
     * `Downloader.buildFileName()` writes `<episodeSlug>_<FORMAT>_<resolution>.<ext>`.
     * The pattern is intentionally strict: it is only used to recognize downloads
     * that predate the provenance registry, never to trust arbitrary user videos.
     */
    private val downloadNamePattern = Regex(
        "^.+_(?:MKV|MP4|X265)_(?:360p|480p|720p|1080p|HD)\\." +
            "(?:mkv|mp4|webm|avi|mov|m4v|ts)$",
        RegexOption.IGNORE_CASE,
    )

    /** True when the name matches the file name this app's downloader generates. */
    fun looksLikeAppDownloadName(fileName: String): Boolean {
        val name = fileName.trim()
        return EpisodeFiles.isVideo(name) && downloadNamePattern.matches(name)
    }
}
