package moe.kabii.data.relational.streams

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.select

// representing a single livestream event on YT - with a video ID
object DBYoutubeStreams {
    object YoutubeStreams : IntIdTable() {
        // only one 'stream' per channel at a time - this can be unique constraint
        val streamChannel = reference("assoc_stream_channel_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE).uniqueIndex()
        val youtubeVideoId = varchar("youtube_id", 11)
        val lastTitle = text("last_title")
        val lastThumbnail = text("thumbnail_url")
        val lastChannelName = text("last_channel_name")
        val lastAvatar = text("last_user_avatar").nullable()

        val peakViewers = integer("peak_viewers")
        val uptimeTicks = integer("uptime_ticks")
        val averageViewers = integer("average_viewers")
    }

    class YoutubeStream(id: EntityID<Int>) : IntEntity(id) {
        var streamChannel by TrackedStreams.StreamChannel referencedOn YoutubeStreams.streamChannel
        var youtubeVideoId by YoutubeStreams.youtubeVideoId
        var lastTitle by YoutubeStreams.lastTitle
        var lastThumbnail by YoutubeStreams.lastThumbnail
        var lastChannelName by YoutubeStreams.lastChannelName
        var lastAvatar by YoutubeStreams.lastAvatar

        var peakViewers by YoutubeStreams.peakViewers
        var uptimeTicks by YoutubeStreams.uptimeTicks
        var averageViewers by YoutubeStreams.averageViewers

        companion object : IntEntityClass<YoutubeStream>(YoutubeStreams) {

            fun findStream(channelId: String): SizedIterable<YoutubeStream> {
                return YoutubeStream.wrapRows(
                    YoutubeStreams
                        .innerJoin(TrackedStreams.StreamChannels)
                        .select {
                            TrackedStreams.StreamChannels.siteChannelID eq channelId
                        }
                )
            }
        }

        fun currentViewers(current: Int) {
            if(current > peakViewers) peakViewers = current
            averageViewers += (current - averageViewers) / ++uptimeTicks
        }
    }
}

