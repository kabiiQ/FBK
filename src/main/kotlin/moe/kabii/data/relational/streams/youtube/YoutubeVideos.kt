package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.streams.TrackedStreams
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime

object YoutubeVideos : LongIdTable() {
    val videoId = char("video_id", 11).uniqueIndex()
    val ytChannel = reference("yt_channel", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
    val lastAPICall = datetime("last_api_call").nullable()
    val liveEvent = reference("live_event", YoutubeLiveEvents, ReferenceOption.SET_NULL).nullable()
    val scheduledEvent = reference("scheduled_event", YoutubeScheduledEvents, ReferenceOption.SET_NULL).nullable()
}

class YoutubeVideo(id: EntityID<Long>) : LongEntity(id) {
    var videoId by YoutubeVideos.videoId
    var ytChannel by TrackedStreams.StreamChannel referencedOn YoutubeVideos.ytChannel
    var lastAPICall by YoutubeVideos.lastAPICall
    var liveEvent by YoutubeLiveEvent optionalReferencedOn YoutubeVideos.liveEvent
    var scheduledEvent by YoutubeScheduledEvent optionalReferencedOn YoutubeVideos.scheduledEvent

    companion object : LongEntityClass<YoutubeVideo>(YoutubeVideos) {
        fun getVideo(videoId: String): YoutubeVideo? = find {
            YoutubeVideos.videoId eq videoId
        }.firstOrNull()

        fun getOrInsert(videoId: String, channelId: String): YoutubeVideo {
            val channel = TrackedStreams.StreamChannel.getOrInsert(TrackedStreams.DBSite.YOUTUBE, channelId)

            return getVideo(videoId) ?: new {
                this.videoId = videoId
                this.ytChannel = channel
                this.lastAPICall = null
                this.liveEvent = null
                this.scheduledEvent = null
            }
        }
    }
}



