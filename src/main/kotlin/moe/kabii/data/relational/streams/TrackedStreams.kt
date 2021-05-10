package moe.kabii.data.relational.streams

import discord4j.common.util.Snowflake
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.WebSubSubscription
import moe.kabii.data.relational.streams.youtube.WebSubSubscriptions
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TwitcastingTarget
import moe.kabii.discord.trackers.TwitchTarget
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.datetime

// generic logic to handle tracking any stream source
object TrackedStreams {
    // Basic enum, more rigid than StreamingTarget - this enum will be relied upon for deserialization
    enum class DBSite(val targetType: StreamingTarget) {
        TWITCH(TwitchTarget),
        YOUTUBE(YoutubeTarget),
        TWITCASTING(TwitcastingTarget)
    }

    object StreamChannels : IntIdTable() {
        val site = enumeration("site_id", DBSite::class)
        val siteChannelID = varchar("site_channel_id", 64).uniqueIndex()
        val lastKnownUsername = varchar("last_known_username", 64).nullable()
    }

    class StreamChannel(id: EntityID<Int>) : IntEntity(id) {
        var site by StreamChannels.site
        var siteChannelID by StreamChannels.siteChannelID
        var lastKnownUsername by StreamChannels.lastKnownUsername

        val targets by Target referrersOn Targets.streamChannel
        val mentionRoles by Mention referrersOn Mentions.streamChannel

        val subscription by WebSubSubscription referrersOn WebSubSubscriptions.webSubChannel

        companion object : IntEntityClass<StreamChannel>(StreamChannels) {

            @WithinExposedContext
            fun getChannel(site: DBSite, channelId: String): StreamChannel? = find {
                StreamChannels.site eq site and
                        (StreamChannels.siteChannelID eq channelId)
            }.firstOrNull()

            @WithinExposedContext
            fun getOrInsert(site: DBSite, channelId: String, username: String? = null): StreamChannel {
                val existing = getChannel(site, channelId)
                return if(existing != null) existing else {
                    val new = new {
                        this.site = site
                        this.siteChannelID = channelId
                        if(username != null) this.lastKnownUsername = username
                    }
                    new
                }
            }

            fun getActive(op: SqlExpressionBuilder.()-> Op<Boolean>) = find(op)
                .filter { chan -> !chan.targets.empty() }
        }
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

        companion object : IntEntityClass<Target>(Targets) {

            // get target with same discord channel and streaming channel id
            @WithinExposedContext
            fun getForChannel(discordChan: Snowflake, site: DBSite, channelId: String) = Target.wrapRows(
                Targets
                    .innerJoin(StreamChannels)
                    .innerJoin(DiscordObjects.Channels).select {
                        StreamChannels.site eq site and
                                (StreamChannels.siteChannelID eq  channelId) and
                                (DiscordObjects.Channels.channelID eq discordChan.asLong())
                    }
            ).firstOrNull()

            @WithinExposedContext
            fun getForGuild(guildId: Snowflake, stream: StreamChannel) = Target.wrapRows(
                Targets
                    .innerJoin(DiscordObjects.Channels
                        .innerJoin(DiscordObjects.Guilds))
                    .select {
                        Targets.streamChannel eq stream.id and
                                (DiscordObjects.Guilds.guildID eq guildId.asLong())
                    }
            )
        }
    }

    object Mentions : IntIdTable() {
        val streamChannel = reference("assoc_stream", StreamChannels, ReferenceOption.CASCADE)
        val guild = reference("assoc_guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val mentionRole = long("discord_mention_role_id")
        val lastMention = datetime("last_role_mention_time").nullable()

        override val primaryKey = PrimaryKey(streamChannel, guild)
    }

    class Mention(id: EntityID<Int>) : IntEntity(id) {
        var stream by StreamChannel referencedOn Mentions.streamChannel
        var guild by DiscordObjects.Guild referencedOn Mentions.guild
        var mentionRole by Mentions.mentionRole
        var lastMention by Mentions.lastMention

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
}