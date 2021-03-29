package moe.kabii.discord.trackers.videos.twitcasting.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil

@JsonClass(generateAdapter = true)
data class TwitcastingUserResponse(
    val user: TwitcastingUser
)

@JsonClass(generateAdapter = true)
data class TwitcastingUser(
    @Json(name = "id") val userId: String,
    @Json(name = "screen_id") val screenId: String,
    val name: String,
    @Json(name = "image") val imageUrl: String,
    @Json(name = "last_movie_id") val movieId: String?,
    @Json(name = "is_live") val live: Boolean
) {
    @Transient val url = URLUtil.StreamingSites.TwitCasting.channelByName(screenId)
}