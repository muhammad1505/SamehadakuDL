package app.samehadaku.dl.core

/**
 * Files the app writes for an episode. Koleksi only counts these, so a shared
 * download folder (or a folder created by another app) can never appear as an
 * anime folder just because it holds images, documents or caches.
 */
object EpisodeFiles {

    private val videoExtensions = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v", "ts")
    private val archiveExtensions = setOf("rar", "zip")

    fun isEpisode(fileName: String): Boolean {
        val name = fileName.trim()
        if (name.isEmpty() || name.startsWith(".")) return false
        if (name.endsWith(".part", ignoreCase = true)) return false
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in videoExtensions || extension in archiveExtensions
    }
}
