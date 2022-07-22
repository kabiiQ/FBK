package moe.kabii.trackers.anime.mal

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object MALAPIMapping {

    @JsonClass(generateAdapter = true)
    data class AnimeListResponse(
        val data: List<UserAnimeListEdge>,
        val paging: Pagination?
    )

    @JsonClass(generateAdapter = true)
    data class UserAnimeListEdge(
        val node: Media,
        @Json(name= "list_status") val listStatus: AnimeListStatus
    )

    @JsonClass(generateAdapter = true)
    data class AnimeListStatus(
        val status: String?,
        val score: Int,
        @Json(name = "num_episodes_watched") val watched: Int,
        @Json(name = "is_rewatching") val rewatching: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class MangaListResponse(
        val data: List<UserMangaListEdge>,
        val paging: Pagination?
    )

    @JsonClass(generateAdapter = true)
    data class UserMangaListEdge(
        val node: Media,
        @Json(name = "list_status") val listStatus: MangaListStatus
    )

    @JsonClass(generateAdapter = true)
    data class MangaListStatus(
        val status: String,
        val score: Int,
        @Json(name = "num_volumes_read") val volumesRead: Int,
        @Json(name = "num_chapters_read") val chaptersRead: Int,
        @Json(name = "is_rereading") val rereading: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class Media(
        val id: Int,
        val title: String,
        @Json(name = "main_picture") val image: Picture?,
        val mean: Float?,
        @Json(name = "nsfw") val _nsfw: String?,
        @Json(name = "num_episodes") val numEpisodes: Int?,
        @Json(name = "num_volumes") val numVolumes: Int?,
        @Json(name = "num_chapters") val numChapters: Int?
    ) {
        @Transient val nsfw = _nsfw != null && _nsfw != "white"
    }

    @JsonClass(generateAdapter = true)
    data class Picture(
        @Json(name = "large") val _large: String?,
        @Json(name = "medium") val _medium: String
    ) {
        @Transient val image = _large ?: _medium
    }

    @JsonClass(generateAdapter = true)
    data class Pagination(
        val previous: String?,
        val next: String?
    )
}