package moe.kabii.discord.trackers.videos.youtube.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.rusty.Try
import java.time.Duration
import java.time.Instant

@JsonClass(generateAdapter = true)
data class YoutubeVideoResponse(
    val items: List<YoutubeVideo>
)

@JsonClass(generateAdapter = true)
data class YoutubeVideo(
    val id: String,
    val snippet: YoutubeVideoSnippet,
    val contentDetails: YoutubeVideoContentDetails,
    val liveStreamingDetails: YoutubeVideoLiveDetails?
)

@JsonClass(generateAdapter = true)
data class YoutubeVideoSnippet(
    val channelId: String,
    val title: String,
    val description: String,
    val thumbnails: YoutubeThumbnails,
    val channelTitle: String,
    @Json(name="liveBroadcastContent") val _liveBroadcastContent: String
) {

    // "live", "none" , or "upcoming"
    @Transient val live: Boolean = _liveBroadcastContent == "live"
    @Transient val upcoming: Boolean = _liveBroadcastContent == "upcoming"
}

@JsonClass(generateAdapter = true)
data class YoutubeVideoContentDetails(
    @Json(name="duration") val _rawDuration: String
) {

    @Transient val duration: Duration? = Try {
        // shouldn't fail but we definitely do not want an exception here
        Duration.parse(_rawDuration)
    }.result.orNull()
}

@JsonClass(generateAdapter = true)
data class YoutubeVideoLiveDetails(
    @Json(name="actualStartTime") val _startTime: String?,
    @Json(name="concurrentViewers") val _concurrentViewers: String?,
    @Json(name="actualEndTime") val _endTime: String?,
    @Json(name="scheduledStartTime") val _scheduledStartTime: String?
) {
    @Transient val startTime = _startTime?.run(Instant::parse)
    @Transient val concurrentViewers = _concurrentViewers?.toIntOrNull()
    @Transient val endTime = _endTime?.run(Instant::parse)
    @Transient val scheduledStartTime = _scheduledStartTime?.run(Instant::parse)
}