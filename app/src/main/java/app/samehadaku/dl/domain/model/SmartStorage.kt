package app.samehadaku.dl.domain.model

/** Opt-in configuration for automatic removal of watched episodes. */
data class SmartStorageSettings(
    val enabled: Boolean = false,
    val graceDays: Int = 3,
)

/**
 * One downloaded video that the user watched or marked watched. [size] is the
 * byte length observed when the marker was created; deletion only proceeds when
 * the file still has exactly that size.
 */
data class WatchedEpisode(
    val folderName: String,
    val fileName: String,
    val size: Long,
    val watchedAt: Long,
    val manual: Boolean = false,
)

/**
 * Playback recorded when the app hands a video to an external player. The
 * duration comes from MediaMetadataRetriever, so no internal player is required.
 *
 * Watching is accumulated per sitting instead of measured as one wall-clock span:
 * [watchedMs] holds the time credited from earlier sittings and [sessionStartedAt]
 * marks the current sitting, which starts when the player is launched and ends
 * when the app becomes visible again. A long pause between sittings therefore
 * does not throw the progress away, and reopening an episode continues it.
 * [sessionStartedAt] is 0 while the app is in the foreground.
 */
data class PendingPlayback(
    val folderName: String,
    val fileName: String,
    val size: Long,
    val startedAt: Long,
    val durationMs: Long,
    val watchedMs: Long = 0L,
    val sessionStartedAt: Long = 0L,
)

/** Live counters for the Smart Storage section in Settings. */
data class SmartStorageStats(
    val watchedCount: Int = 0,
    val watchedBytes: Long = 0L,
    val deletedCount: Int = 0,
    val reclaimedBytes: Long = 0L,
    val lastCleanupAt: Long = 0L,
    val lastCleanupDeleted: Int = 0,
)
