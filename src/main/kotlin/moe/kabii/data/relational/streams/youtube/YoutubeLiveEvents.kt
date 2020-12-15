package moe.kabii.data.relational.streams.youtube

import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBTwitchStreams
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
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
}

class YoutubeLiveEvent(id: EntityID<Long>) : LongEntity(id) {
    var ytVideo by YoutubeVideo referencedOn YoutubeLiveEvents.ytVideo
    var lastThumbnail by YoutubeLiveEvents.lastThumbnail
    var lastChannelName by YoutubeLiveEvents.lastChannelName

    var peakViewers by YoutubeLiveEvents.peakViewers
    var uptimeTicks by YoutubeLiveEvents.uptimeTicks
    var averageViewers by YoutubeLiveEvents.averageViewers

    fun updateViewers(current: Int) {
        if(current > peakViewers) peakViewers = current
        averageViewers += (current - averageViewers) / ++uptimeTicks
    }

    companion object : LongEntityClass<YoutubeLiveEvent>(YoutubeLiveEvents) {
        fun liveEventFor(video: YoutubeVideo): YoutubeLiveEvent? = find {
            YoutubeLiveEvents.ytVideo eq video.id
        }.firstOrNull()
    }
}