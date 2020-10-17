package moe.kabii.discord.trackers.streams.youtube.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YoutubeThumbnails(
    val default: YoutubeThumbnail,
    val medium: YoutubeThumbnail? = null,
    val high: YoutubeThumbnail? = null,
    val standard: YoutubeThumbnail? = null,
    val maxres: YoutubeThumbnail? = null,
)

@JsonClass(generateAdapter = true)
data class YoutubeThumbnail(
    val url: String
)