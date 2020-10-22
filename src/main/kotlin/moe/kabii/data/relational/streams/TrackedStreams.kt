package moe.kabii.data.relational.streams

import discord4j.common.util.Snowflake
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TwitchTarget
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.structure.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

// generic logic to handle tracking any stream source
object TrackedStreams {
    // Basic enum, more rigid than StreamingTarget - this enum will be relied upon for deserialization
    enum class DBSite(val targetType: StreamingTarget) {
        TWITCH(TwitchTarget),
        YOUTUBE(YoutubeTarget)
    }

    object StreamChannels : IntIdTable() {
        val site = enumeration("site_id", DBSite::class)
        val siteChannelID = varchar("site_channel_id", 64).uniqueIndex()
    }

    class StreamChannel(id: EntityID<Int>) : IntEntity(id) {
        var site by StreamChannels.site
        var siteChannelID by StreamChannels.siteChannelID

        val targets by Target referrersOn Targets.streamChannel
        val notifications by Notification referrersOn Notifications.channelID
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
            fun getMentionsFor(guildID: Snowflake, streamChannelID: String) = Mention.wrapRows(
                Mentions
                    .innerJoin(StreamChannels)
                    .innerJoin(DiscordObjects.Guilds)
                    .select {
                        DiscordObjects.Guilds.guildID eq guildID.asLong() and
                                (StreamChannels.siteChannelID eq streamChannelID)
                    })
        }
    }

    object Notifications : IntIdTable() {
        val targetID = reference("assoc_target_id", Targets, ReferenceOption.CASCADE).uniqueIndex()
        val channelID = reference("channel_id", StreamChannels, ReferenceOption.CASCADE)
        val message = reference("message_id", MessageHistory.Messages, ReferenceOption.CASCADE)
    }

    class Notification(id: EntityID<Int>) : IntEntity(id) {
        var targetID by Target referencedOn Notifications.targetID
        var channelID by StreamChannel referencedOn Notifications.channelID
        var messageID by MessageHistory.Message referencedOn Notifications.message

        companion object : IntEntityClass<Notification>(Notifications)
    }
}