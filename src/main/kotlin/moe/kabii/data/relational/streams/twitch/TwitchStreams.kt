package moe.kabii.data.relational.streams.twitch

import moe.kabii.data.relational.streams.TrackedStreams
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select

// representing an actual single 'livestream' event from Twitch
object DBTwitchStreams {
    object TwitchStreams : IntIdTable() {
        val channelID = reference("assoc_stream_channel_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE).uniqueIndex()
        val startTime = datetime("started_at")
        val peakViewers = integer("peak_viewers")
        val uptimeTicks = integer("uptime_ticks")
        val averageViewers = integer("average_viewers")
        val lastTitle = text("last_title")
        val lastGame = text("last_game_name")
    }

    class TwitchStream(id: EntityID<Int>) : IntEntity(id) {
        var channelID by TrackedStreams.StreamChannel referencedOn TwitchStreams.channelID
        var startTime by TwitchStreams.startTime
        var peakViewers by TwitchStreams.peakViewers
        var uptimeTicks by TwitchStreams.uptimeTicks
        var averageViewers by TwitchStreams.averageViewers
        var lastTitle by TwitchStreams.lastTitle
        var lastGame by TwitchStreams.lastGame

        companion object : IntEntityClass<TwitchStream>(TwitchStreams) {
            fun getStreamDataFor(channelId: Long): SizedIterable<TwitchStream> {
                val twitchId = channelId.toString()
                return TwitchStream.wrapRows(
                    TwitchStreams
                        .innerJoin(TrackedStreams.StreamChannels)
                        .select {
                            TrackedStreams.StreamChannels.siteChannelID eq twitchId
                        }
                )
            }
        }

        fun updateViewers(current: Int) {
            if (current > peakViewers) peakViewers = current
            averageViewers += (current - averageViewers) / ++uptimeTicks
        }
    }
}