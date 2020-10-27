package moe.kabii.discord.trackers.streams.youtube

import java.time.Duration
import java.time.Instant

data class YoutubeChannelInfo(
    val id: String,
    val name: String,
    val avatar: String?
) {
    val url = "https://youtube.com/channel/$id"
}

data class YoutubeVideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val thumbnail: String,
    val live: Boolean,
    val duration: Duration?,
    val liveInfo: YoutubeStreamInfo?,
    val channel: YoutubeChannelInfo,
) {
    val url = "https://youtube.com/watch?v=$id"
}

data class YoutubeStreamInfo(
    val startTime: Instant?,
    val concurrent: Int?,
    val endTime: Instant?,
)