package moe.kabii.net.api.videos

import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.util.extensions.RequiresExposedContext

@JsonClass(generateAdapter = true)
data class YoutubeVideoResponse(
    val matchingCount: Int,
    val totalAvailable: Int,
    val videos: List<Video>
) {

    @JsonClass(generateAdapter = true)
    data class Video(
        val id: String,
        val type: String?,
        val lastKnownTitle: String?,
        val lastUpdate: String?,

        val liveEvent: LiveEvent?,
        val scheduledEvent: ScheduledEvent?
    )

    @JsonClass(generateAdapter = true)
    data class LiveEvent(
        val lastThumbnail: String,
        val guessPremiere: Boolean,
        val peakViewers: Int,
        val averageViewers: Int
    )

    @JsonClass(generateAdapter = true)
    data class ScheduledEvent(
        val scheduledStart: String
    )

    fun toJson(): String = adapter.indent("  ").toJson(this)

    companion object {
        private val adapter = MOSHI.adapter(YoutubeVideoResponse::class.java)

        @RequiresExposedContext fun getType(video: YoutubeVideo) = when {
            video.lastAPICall == null -> null
            video.liveEvent != null -> "live"
            video.scheduledEvent != null -> "scheduled"
            else -> "past"
        }

        @RequiresExposedContext
        fun forVideos(matchingVideos: List<YoutubeVideo>, dbTotal: Int) = YoutubeVideoResponse(
            matchingCount = matchingVideos.size,
            totalAvailable = dbTotal,
            videos = matchingVideos.map { video ->
                Video(
                    id = video.videoId,
                    type = getType(video),
                    lastKnownTitle = video.lastTitle,
                    lastUpdate = video.lastAPICall?.toString(),
                    liveEvent = if(video.liveEvent == null) null else {
                        val live = video.liveEvent!!
                        LiveEvent(
                            lastThumbnail = live.lastThumbnail,
                            guessPremiere = live.premiere,
                            peakViewers = live.peakViewers,
                            averageViewers = live.averageViewers
                        )
                    },
                    scheduledEvent = if(video.scheduledEvent == null) null else {
                        val scheduled = video.scheduledEvent!!
                        ScheduledEvent(
                            scheduledStart = scheduled.scheduledStart.toString()
                        )
                    }
                )
            }
        )
    }
}

@JsonClass(generateAdapter = true)
data class YoutubeChannelResponse(
    val id: String,
    val lastKnownUsername: String,
    val lastApiUsage: String?
) {
    companion object {
        private val adapter = MOSHI.adapter(YoutubeChannelResponse::class.java)

        @RequiresExposedContext
        fun generate(channel: TrackedStreams.StreamChannel): String =
            YoutubeChannelResponse(
                id = channel.siteChannelID,
                lastKnownUsername = channel.lastKnownUsername ?: "",
                lastApiUsage = channel.lastApiUsage?.toString()
            ).run(adapter.indent("  ")::toJson)
    }
}