package app.samehadaku.dl.core

import app.samehadaku.dl.domain.model.DetectionResult
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.domain.model.VideoFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor() {

    @Volatile
    var pendingVerificationUrl: String? = null

    @Volatile
    var detection: DetectionResult? = null

    @Volatile
    var selectedEpisodes: List<Episode> = emptyList()

    @Volatile
    var selectedFormat: VideoFormat? = null

    @Volatile
    var selectedResolution: String? = null

    @Volatile
    var selectedHost: Host? = null

    /** Report from the last enqueue (how many links were dead) shown on the queue screen. */
    @Volatile
    var queueNotice: String? = null

    /** Search result currently opened in the detail screen. */
    @Volatile
    var selectedSearchResult: AnimeSearchResult? = null

    fun saveDetection(result: DetectionResult) {
        detection = result
        selectedEpisodes = result.episodes
        selectedFormat = null
        selectedResolution = null
        selectedHost = null
    }

    fun saveSelection(format: VideoFormat, resolution: String, host: Host) {
        selectedFormat = format
        selectedResolution = resolution
        selectedHost = host
    }

    fun clear() {
        detection = null
        selectedEpisodes = emptyList()
        selectedFormat = null
        selectedResolution = null
        selectedHost = null
        selectedSearchResult = null
    }
}
