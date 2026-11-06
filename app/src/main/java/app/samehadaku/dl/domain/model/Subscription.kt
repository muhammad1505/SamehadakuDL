package app.samehadaku.dl.domain.model

/**
 * An anime the user follows. [lastEpisode] is the highest episode that has been
 * downloaded successfully (or was set manually), so "Lanjutkan" only fetches
 * episodes newer than that.
 */
data class Subscription(
    val name: String,
    val animeUrl: String,
    val slug: String,
    val lastEpisode: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Format/resolution/host the user picked last; reused for later downloads. */
data class QualityPreference(
    val format: VideoFormat,
    val resolution: String,
    val host: Host,
)
