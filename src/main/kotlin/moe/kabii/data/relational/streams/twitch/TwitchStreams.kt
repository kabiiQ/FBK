package moe.kabii.data.relational.streams.twitch

import moe.kabii.data.relational.discord.MessageHistory
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

    object Notifications : IntIdTable() {
        val targetID = reference("assoc_target_id", TrackedStreams.Targets, ReferenceOption.CASCADE).uniqueIndex()
        val channelID = reference("channel_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE)
        val message = reference("message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
        val deleted = bool("notif_deleted").default(false)
    }

    class Notification(id: EntityID<Int>) : IntEntity(id) {
        var targetID by TrackedStreams.Target referencedOn Notifications.targetID
        var channelID by TrackedStreams.StreamChannel referencedOn Notifications.channelID
        var messageID by MessageHistory.Message referencedOn Notifications.message
        // if the discord message is deleted, we don't need to keep requesting it from Discord, and we should not re-post this exact notification.
        var deleted by Notifications.deleted

        companion object : IntEntityClass<Notification>(Notifications) {
            fun getForChannel(dbChannel: TrackedStreams.StreamChannel) = find {
                Notifications.channelID eq dbChannel.id
            }

            fun getForTarget(dbTarget: TrackedStreams.Target) = find {
                Notifications.targetID eq dbTarget.id
            }
        }
    }
}