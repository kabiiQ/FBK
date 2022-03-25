package moe.kabii.trackers.videos.twitcasting.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class TwitcastingMovieResponse(
    val movie: TwitcastingMovie,
    val broadcaster: TwitcastingUser,
    val signature: String?
)

@JsonClass(generateAdapter = true)
data class TwitcastingMovie(
    @Json(name = "id") val movieId: String,
    val title: String,
    val subtitle: String?,
    val link: String,
    @Json(name = "is_live") val live: Boolean,
    @Json(name = "small_thumbnail") val thumbnailUrl: String,
    @Json(name = "duration") val lengthSeconds: Int,
    @Json(name = "created") val _createdTimestamp: Long,
    @Json(name = "total_view_count") val views: Int
) {
    @Transient val created = Instant.ofEpochSecond(_createdTimestamp)
}