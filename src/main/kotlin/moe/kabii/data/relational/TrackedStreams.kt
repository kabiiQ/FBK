package moe.kabii.data.relational

import discord4j.common.util.Snowflake
import moe.kabii.discord.trackers.streams.StreamParser
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select

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
    }

    object StreamChannels : IntIdTable() {
        val site = enumeration("site_id", Site::class)
        val siteChannelID = long("site_channel_id").uniqueIndex()
    }

    class StreamChannel(id: EntityID<Int>) : IntEntity(id) {
        var site by StreamChannels.site
        var siteChannelID by StreamChannels.siteChannelID

        val targets by Target referrersOn Targets.streamChannel
        val notifications by Notification referrersOn Notifications.channelID
        val streams by Stream referrersOn Streams.channelID
        val mentionRoles by Mention referrersOn Mentions.streamChannel

        companion object : IntEntityClass<StreamChannel>(StreamChannels)
    }

    object Targets : IntIdTable() {
        val streamChannel = reference("assoc_stream_channel", StreamChannels, ReferenceOption.CASCADE)
        val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val tracker = reference("discord_user_tracked", DiscordObjects.Users, ReferenceOption.CASCADE)
    }

    class Target(id: EntityID<Int>) : IntEntity(id) {
        var streamChannel by StreamChannel referencedOn Targets.streamChannel
        var discordChannel by DiscordObjects.Channel referencedOn Targets.discordChannel
        var tracker by DiscordObjects.User referencedOn Targets.tracker

        val notifications by Notification referrersOn Notifications.targetID

        companion object : IntEntityClass<Target>(Targets)
    }

    object Mentions : IntIdTable() {
        val streamChannel = reference("assoc_stream", StreamChannels, ReferenceOption.CASCADE)
        val guild = reference("assoc_guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val mentionRole = long("discord_mention_role_id").uniqueIndex()
        val isAutomaticSet = bool("is_automatic")
    }

    class Mention(id: EntityID<Int>) : IntEntity(id) {
        var stream by StreamChannel referencedOn Mentions.streamChannel
        var guild by DiscordObjects.Guild referencedOn Mentions.guild
        var mentionRole by Mentions.mentionRole
        var isAutomaticSet by Mentions.isAutomaticSet

        companion object : IntEntityClass<Mention>(Mentions) {
            @WithinExposedContext
            fun getMentionsFor(guildID: Snowflake, streamChannelID: Long) = Mention.wrapRows(
                Mentions
                    .innerJoin(StreamChannels)
                    .innerJoin(DiscordObjects.Guilds).select {
                        DiscordObjects.Guilds.guildID eq guildID.asLong() and
                                (StreamChannels.siteChannelID eq streamChannelID)
                    })
        }
    }

    object Streams : IntIdTable() {
        val channelID = reference("assoc_stream_channel_id", StreamChannels, ReferenceOption.CASCADE).uniqueIndex()
        val startTime = datetime("started_at")
        val peakViewers = integer("peak_viewers")
        val uptimeTicks = integer("uptime_ticks")
        val averageViewers = integer("average_viewers")
        val lastTitle = text("last_title")
        val lastGame = text("last_game_name")
    }

    class Stream(id: EntityID<Int>) : IntEntity(id) {
        var channelID by StreamChannel referencedOn Streams.channelID
        var startTime by Streams.startTime
        var peakViewers by Streams.peakViewers
        var uptimeTicks by Streams.uptimeTicks
        var averageViewers by Streams.averageViewers
        var lastTitle by Streams.lastTitle
        var lastGame by Streams.lastGame

        companion object : IntEntityClass<Stream>(Streams)

        fun updateViewers(current: Int) {
            if(current > peakViewers) peakViewers = current
            averageViewers += (current - averageViewers) / ++uptimeTicks
        }
    }

    object Notifications : IntIdTable() {
        val targetID = reference("assoc_target_id", Targets, ReferenceOption.CASCADE).uniqueIndex()
        val channelID = reference("channel_id", StreamChannels, ReferenceOption.CASCADE)
        val message = reference("message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
        val stream = reference("stream_id", Streams, ReferenceOption.SET_NULL).nullable()
    }

    class Notification(id: EntityID<Int>) : IntEntity(id) {
        var targetID by Target referencedOn Notifications.targetID
        var channelID by StreamChannel referencedOn Notifications.channelID
        var messageID by MessageHistory.Message referencedOn Notifications.message
        var stream by Stream optionalReferencedOn Notifications.stream

        companion object : IntEntityClass<Notification>(Notifications)
    }
}