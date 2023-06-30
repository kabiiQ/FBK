package moe.kabii.data.relational.streams.twitch

import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.videos.StreamWatcher
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select

object DBStreams {
    /*
    Object representing single 'livestream' event from Twitch
    Now also can represent a Kick stream - the structure is identical
     */
    object LiveStreamEvents : IdTable<Int>("twitchstreams") {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val channelID = reference("assoc_stream_channel_id", TrackedStreams.StreamChannels, ReferenceOption.CASCADE).uniqueIndex()
        val startTime = datetime("started_at")
        val peakViewers = integer("peak_viewers")
        val uptimeTicks = integer("uptime_ticks")
        val averageViewers = integer("average_viewers")
        val lastTitle = text("last_title")
        val lastGame = text("last_game_name")

        override val primaryKey = PrimaryKey(channelID)
    }

    class LiveStreamEvent(id: EntityID<Int>) : IntEntity(id) {
        var channelID by TrackedStreams.StreamChannel referencedOn LiveStreamEvents.channelID
        var startTime by LiveStreamEvents.startTime
        var peakViewers by LiveStreamEvents.peakViewers
        var uptimeTicks by LiveStreamEvents.uptimeTicks
        var averageViewers by LiveStreamEvents.averageViewers
        var lastTitle by LiveStreamEvents.lastTitle
        var lastGame by LiveStreamEvents.lastGame

        companion object : IntEntityClass<LiveStreamEvent>(LiveStreamEvents) {
            fun getTwitchStreamFor(channelId: Long): LiveStreamEvent? {
                val twitchId = channelId.toString()
                return LiveStreamEvent.wrapRows(
                    LiveStreamEvents
                        .innerJoin(TrackedStreams.StreamChannels)
                        .select {
                            TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCH and
                                    (TrackedStreams.StreamChannels.siteChannelID eq twitchId)
                        }
                ).firstOrNull()
            }

            fun getKickStreamFor(dbChannel: TrackedStreams.StreamChannel) = find {
                LiveStreamEvents.channelID eq dbChannel.id
            }.firstOrNull()

//            fun getKickStreamFor(channel: String): LiveStreamEvent? {
//                return LiveStreamEvent.wrapRows(
//                    LiveStreamEvents
//                        .innerJoin(TrackedStreams.StreamChannels)
//                        .select {
//                            TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.KICK and
//                                    (LowerCase(TrackedStreams.StreamChannels.siteChannelID) eq channel.lowercase())
//                        }
//                ).firstOrNull()
//            }
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

            fun getForTarget(dbTarget: StreamWatcher.TrackedTarget) = find {
                Notifications.targetID eq dbTarget.db
            }
        }
    }
}