package app.samehadaku.dl.domain.model

data class AnimeDetail(
    val description: String? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
)

data class ReleaseScheduleItem(
    val day: String,
    val title: String,
    val time: String? = null,
    val episode: String? = null,
    val status: String? = null,
    val url: String? = null,
)
