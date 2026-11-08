package app.samehadaku.dl.domain.model

/** One anime card produced by a Samehadaku search page. */
data class AnimeSearchResult(
    val title: String,
    val url: String,
    val slug: String,
    val type: String? = null,
    val episodeLabel: String? = null,
)
