package moe.kabii.discord.trackers.videos.twitcasting.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TwitcastingErrorResponse(
    val error: TwitcastingError
)

@JsonClass(generateAdapter = true)
data class TwitcastingError(
    val code: Int,
    val message: String
)
