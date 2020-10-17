package moe.kabii.discord.trackers.streams.youtube.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YoutubeChannelResponse(
    val items: List<YoutubeChannel>
)

@JsonClass(generateAdapter = true)
data class YoutubeChannel(
    val id: String,
    val snippet: YoutubeChannelSnippet
)

@JsonClass(generateAdapter = true)
data class YoutubeChannelSnippet(
    val title: String,
    val thumbnails: YoutubeThumbnails
)

