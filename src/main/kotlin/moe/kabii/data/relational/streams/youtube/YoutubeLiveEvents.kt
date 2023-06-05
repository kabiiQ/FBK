package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.mongodb.guilds.YoutubeSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.videos.StreamWatcher
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and

object YoutubeLiveEvents : LongIdTable() {
    val ytVideo = reference("yt_video", YoutubeVideos, ReferenceOption.CASCADE).uniqueIndex()
    val lastThumbnail = text("thumbnail_url")
    val lastChannelName = text("channel_name")

    val peakViewers = integer("peak_viewers")
    val uptimeTicks = integer("uptime_ticks")
    val averageViewers = integer("average_viewers")

    val premiere = bool("is_premiere")
}

class YoutubeLiveEvent(id: EntityID<Long>) : LongEntity(id) {
    var ytVideo by YoutubeVideo referencedOn YoutubeLiveEvents.ytVideo
    var lastThumbnail by YoutubeLiveEvents.lastThumbnail
    var lastChannelName by YoutubeLiveEvents.lastChannelName

    var peakViewers by YoutubeLiveEvents.peakViewers
    var uptimeTicks by YoutubeLiveEvents.uptimeTicks
    var averageViewers by YoutubeLiveEvents.averageViewers

    var premiere by YoutubeLiveEvents.premiere

    fun updateViewers(current: Int) {
        if(current > peakViewers) peakViewers = current
        averageViewers += (current - averageViewers) / ++uptimeTicks
    }

    fun shouldPostLiveNotice(settings: YoutubeSettings): Boolean = when {
        this.premiere -> settings.premieres
        else -> settings.liveStreams
    }

    companion object : LongEntityClass<YoutubeLiveEvent>(YoutubeLiveEvents) {
        fun liveEventFor(video: YoutubeVideo): YoutubeLiveEvent? = find {
            YoutubeLiveEvents.ytVideo eq video.id
        }.firstOrNull()
    }
}

object YoutubeNotifications : IdTable<Int>() {
    override val id = integer("id").autoIncrement().entityId().uniqueIndex()
    val targetID = reference("assoc_target_id", TrackedStreams.Targets, ReferenceOption.CASCADE)
    val videoID = reference("yt_video_id", YoutubeVideos, ReferenceOption.CASCADE)
    val message = reference("message_id", MessageHistory.Messages, ReferenceOption.SET_NULL).nullable()

    override val primaryKey = PrimaryKey(targetID, videoID)
}

class YoutubeNotification(id: EntityID<Int>) : IntEntity(id) {
    var targetID by TrackedStreams.Target referencedOn YoutubeNotifications.targetID
    var videoID by YoutubeVideo referencedOn YoutubeNotifications.videoID
    var messageID by MessageHistory.Message optionalReferencedOn YoutubeNotifications.message

    companion object : IntEntityClass<YoutubeNotification>(YoutubeNotifications) {
        fun getForTarget(dbTarget: TrackedStreams.Target) = find {
            YoutubeNotifications.targetID eq dbTarget.id
        }

        fun getExisting(dbTarget: StreamWatcher.TrackedTarget, dbVideo: YoutubeVideo) = find {
            YoutubeNotifications.targetID eq dbTarget.db and
                    (YoutubeNotifications.videoID eq dbVideo.id)
        }
    }
}