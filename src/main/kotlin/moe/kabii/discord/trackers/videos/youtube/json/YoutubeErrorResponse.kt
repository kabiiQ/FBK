package moe.kabii.discord.trackers.videos.youtube.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YoutubeErrorResponse(
    val error: YoutubeErrorSet
)

@JsonClass(generateAdapter = true)
data class YoutubeErrorSet(
    val errors: List<YoutubeError>
)

@JsonClass(generateAdapter = true)
data class YoutubeError(
    val domain: String,
    val reason: String,
    val message: String
)