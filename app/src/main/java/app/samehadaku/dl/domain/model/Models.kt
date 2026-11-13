package app.samehadaku.dl.domain.model

data class Episode(
    val number: Int,
    val url: String,
    val slug: String,
)

enum class VideoFormat(val label: String) {
    MKV("MKV"),
    MP4("MP4"),
    X265("X265"),
    ;

    companion object {
        fun fromLabel(value: String): VideoFormat? =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
    }
}

enum class Host(val label: String) {
    PIXELDRAIN("Pixeldrain"),
    MEDIAFIRE("Mediafire"),
    GOFILE("Gofile"),
    ACEFILE("Acefile"),
    FILEDON("Filedon"),
    ;

    companion object {
        fun fromLink(url: String, text: String = ""): Host? {
            val haystack = "$url $text".lowercase()
            return when {
                "pixeldrain" in haystack -> PIXELDRAIN
                "mediafire" in haystack -> MEDIAFIRE
                "gofile" in haystack -> GOFILE
                "acefile" in haystack -> ACEFILE
                "filedon" in haystack -> FILEDON
                else -> null
            }
        }
    }
}

enum class DownloadState {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED,
    DONE,
    FAILED,
    SKIPPED,
    CANCELED,
    ;

    val isActive: Boolean
        get() = this == QUEUED || this == RESOLVING || this == DOWNLOADING

    val isFinished: Boolean
        get() = this == DONE || this == FAILED || this == SKIPPED || this == CANCELED
}

data class DownloadLinks(
    val data: Map<VideoFormat, Map<String, Map<Host, String>>> = emptyMap(),
    /** Hosts the site lists but strikes out because the upload is gone. */
    val unavailable: Map<VideoFormat, Map<String, Set<Host>>> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = data.isEmpty()

    fun formats(): List<VideoFormat> = data.keys.toList()

    fun resolutions(format: VideoFormat): List<String> =
        data[format]?.keys?.toList().orEmpty()

    fun hosts(format: VideoFormat, resolution: String): List<Host> =
        data[format]?.get(resolution)?.keys?.toList().orEmpty()

    fun unavailableHosts(format: VideoFormat, resolution: String): List<Host> =
        unavailable[format]?.get(resolution)?.toList().orEmpty()

    fun url(format: VideoFormat, resolution: String, host: Host): String? =
        data[format]?.get(resolution)?.get(host)

    companion object {
        val EMPTY = DownloadLinks()
    }
}

data class QualityOption(
    val format: VideoFormat,
    val resolution: String,
    val host: Host,
    val url: String,
)

data class TargetLink(
    val host: Host,
    val url: String,
)

data class DownloadTask(
    val id: String,
    val episodeUrl: String,
    val episodeNumber: Int,
    val animeSlug: String,
    val format: VideoFormat,
    val resolution: String,
    val preferredHost: Host,
    val state: DownloadState = DownloadState.QUEUED,
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val currentHost: Host? = null,
    val failureReasons: List<String> = emptyList(),
    val note: String? = null,
    val fileName: String? = null,
    val alreadyExisted: Boolean = false,
    /** How many automatic retries were already spent on this task. */
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (bytesDone.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val episodeSlug: String
        get() = episodeUrl.trimEnd('/').substringAfterLast('/')

    companion object {
        fun create(
            episode: Episode,
            animeSlug: String,
            format: VideoFormat,
            resolution: String,
            preferredHost: Host,
            id: String = java.util.UUID.randomUUID().toString(),
        ): DownloadTask = DownloadTask(
            id = id,
            episodeUrl = episode.url,
            episodeNumber = episode.number,
            animeSlug = animeSlug,
            format = format,
            resolution = resolution,
            preferredHost = preferredHost,
        )
    }
}

data class DetectionResult(
    val sourceUrl: String,
    val finalUrl: String,
    val animeSlug: String,
    val episodes: List<Episode>,
    val isListPage: Boolean,
)

sealed interface HostResolveResult {
    /**
     * [headers] are required by some hosts (Gofile) on the file request itself,
     * so they must be forwarded to both the health probe and the download.
     */
    data class Success(
        val directUrl: String,
        val headers: Map<String, String> = emptyMap(),
    ) : HostResolveResult

    /**
     * [permanent] marks a failure the host will not recover from on its own (deleted,
     * takedown, invalid link), which is what makes a link safe to call dead up front.
     */
    data class Failure(
        val reason: String,
        val permanent: Boolean = false,
    ) : HostResolveResult
}

data class EnqueueResult(
    val total: Int,
    val queued: Int,
    val deadEpisodes: List<Int>,
) {
    val deadCount: Int get() = deadEpisodes.size
}
