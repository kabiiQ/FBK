package moe.kabii.data.relational

import moe.kabii.discord.trackers.streams.StreamParser
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TrackedStreams {
    data class StreamQueryInfo(
        val site: Site,
        val id: String
    )

    data class StreamInfo(
        val site: Site,
        val id: Long
    )

    enum class Site(val full: String, val parser: StreamParser) {
        TWITCH("Twitch", TwitchParser);
        //MIXER("Mixer", MixerParser)
    }

    object Channels : IntIdTable() {
        val site = enumeration("site_id", Site::class)
        val channelID = long("channel_id").uniqueIndex()
    }

    class Channel(id: EntityID<Int>) : IntEntity(id) {
        var site by Channels.site
        var channelID by Channels.channelID

        val targets by Target referrersOn Targets.channelID
        val notifications by Notification referrersOn Notifications.channelID
        val streams by Stream referrersOn Streams.channelID

        companion object : IntEntityClass<Channel>(Channels)
    }

    object Targets : IntIdTable() {
        val channelID = reference("channel_id", Channels, ReferenceOption.CASCADE)
        val discordChannel = long("discord_channel")
        val tracker = reference("user", DiscordObjects.Users, ReferenceOption.CASCADE)
        val guild = reference("guild", DiscordObjects.Guilds, ReferenceOption.CASCADE).nullable()
        val mention = long("mention_role_id").nullable()
    }

    class Target(id: EntityID<Int>) : IntEntity(id) {
        var channelID by Channel referencedOn Targets.channelID
        var discordChannel by Targets.discordChannel
        var tracker by DiscordObjects.User referencedOn Targets.tracker
        var guild by DiscordObjects.Guild optionalReferencedOn Targets.guild
        var mention by Targets.mention

        val notifications by Notification referrersOn Notifications.targetID

        companion object : IntEntityClass<Target>(Targets)
    }

    object Streams : IntIdTable() {
        val channelID = reference("channel_id", Channels, ReferenceOption.CASCADE).uniqueIndex()
        val startTime = datetime("started_at")
        val peakViewers = integer("peak_viewers")
        val uptimeTicks = integer("uptime_ticks")
        val averageViewers = integer("average_viewers")
        val lastTitle = text("last_title")
        val lastGame = text("last_game_name")
    }

    class Stream(id: EntityID<Int>) : IntEntity(id) {
        var channelID by Channel referencedOn Streams.channelID
        var startTime by Streams.startTime
        var peakViewers by Streams.peakViewers
        var uptimeTicks by Streams.uptimeTicks
        var averageViewers by Streams.averageViewers
        var lastTitle by Streams.lastTitle
        var lastGame by Streams.lastGame

        companion object : IntEntityClass<Stream>(Streams)

        fun updateViewers(current: Int) {
            if(current > peakViewers) peakViewers = current
            averageViewers += (current) - averageViewers / ++uptimeTicks
        }
    }

    object Notifications : IntIdTable() {
        val targetID = reference("target_id", Targets, ReferenceOption.CASCADE).uniqueIndex()
        val channelID = reference("channel_id", Channels, ReferenceOption.CASCADE)
        val message = reference("message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
        val stream = reference("stream_id", Streams, ReferenceOption.SET_NULL).nullable()
    }

    class Notification(id: EntityID<Int>) : IntEntity(id) {
        var targetID by Target referencedOn Notifications.targetID
        var channelID by Channel referencedOn Notifications.channelID
        var messageID by MessageHistory.Message referencedOn Notifications.message
        var stream by Stream optionalReferencedOn Notifications.stream

        companion object : IntEntityClass<Notification>(Notifications)
    }
}