package app.samehadaku.dl.domain.model

data class AnimeDetail(
    val description: String? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val type: String? = null,
    val source: String? = null,
    val duration: String? = null,
    val totalEpisodes: String? = null,
    val season: String? = null,
    val studio: String? = null,
    val producers: String? = null,
    val released: String? = null,
)

data class ReleaseScheduleItem(
    val day: String,
    val title: String,
    val time: String? = null,
    val episode: String? = null,
    val status: String? = null,
    val url: String? = null,
    val coverUrl: String? = null,
    val rating: String? = null,
)
