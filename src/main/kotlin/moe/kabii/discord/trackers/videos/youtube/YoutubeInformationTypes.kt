package moe.kabii.discord.trackers.videos.youtube

import moe.kabii.util.constants.URLUtil
import java.time.Duration
import java.time.Instant

data class YoutubeChannelInfo(
    val id: String,
    val name: String,
    val avatar: String?
) {
    val url = URLUtil.StreamingSites.Youtube.channel(id)
}

data class YoutubeVideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val thumbnail: String,
    val live: Boolean,
    val upcoming: Boolean,
    val premiere: Boolean,
    val duration: Duration,
    val published: Instant,
    val liveInfo: YoutubeStreamInfo?,
    val channel: YoutubeChannelInfo,
) {
    val url = "https://youtube.com/watch?v=$id"
}

data class YoutubeStreamInfo(
    val startTime: Instant?,
    val concurrent: Int?,
    val endTime: Instant?,
    val scheduledStart: Instant?
)