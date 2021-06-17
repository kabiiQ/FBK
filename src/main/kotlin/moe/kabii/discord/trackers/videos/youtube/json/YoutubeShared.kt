package moe.kabii.discord.trackers.videos.youtube.json

import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil

@JsonClass(generateAdapter = true)
data class YoutubeThumbnails(
    val default: YoutubeThumbnail,
    val medium: YoutubeThumbnail? = null,
    val high: YoutubeThumbnail? = null,
    val standard: YoutubeThumbnail? = null,
    val maxres: YoutubeThumbnail? = null,
) {
    fun thumbnail(videoId: String) = arrayOf(maxres, high, standard).filterNotNull().firstOrNull()?.url ?: URLUtil.StreamingSites.Youtube.thumbnail(videoId)
}

@JsonClass(generateAdapter = true)
data class YoutubeThumbnail(
    val url: String
)