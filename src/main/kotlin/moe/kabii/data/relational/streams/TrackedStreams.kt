package moe.kabii.data.relational.streams

import discord4j.common.util.Snowflake
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.trackers.*
import moe.kabii.util.extensions.WithinExposedContext
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

// generic logic to handle tracking any stream source
object TrackedStreams {
    // Basic enum, more rigid than StreamingTarget - this enum will be relied upon for deserialization
    enum class DBSite(val targetType: StreamingTarget) {
        TWITCH(TwitchTarget),
        YOUTUBE(YoutubeTarget),
        TWITCASTING(TwitcastingTarget),
        SPACES(TwitterSpaceTarget)
    }

    object StreamChannels : IntIdTable() {
        val site = enumeration("site_id", DBSite::class)
        val siteChannelID = varchar("site_channel_id", 64)
        val lastKnownUsername = varchar("last_known_username", 64).nullable()

        val apiUse = bool("tracked_for_external_usage").default(false)
        val lastApiUsage = datetime("last_external_api_usage").nullable()

        init {
            index(isUnique = true, site, siteChannelID)
        }
    }

    class StreamChannel(id: EntityID<Int>) : IntEntity(id) {
        var site by StreamChannels.site
        var siteChannelID by StreamChannels.siteChannelID
        var lastKnownUsername by StreamChannels.lastKnownUsername

        var apiUse by StreamChannels.apiUse
        var lastApiUsage by StreamChannels.lastApiUsage

        val targets by Target referrersOn Targets.streamChannel
        val mentionRoles by Mention referrersOn Mentions.streamChannel

        val subscription by WebSubSubscription referrersOn WebSubSubscriptions.webSubChannel

        companion object : IntEntityClass<StreamChannel>(StreamChannels) {

            @WithinExposedContext
            fun getChannel(site: DBSite, channelId: String): StreamChannel? = find {
                StreamChannels.site eq site and
                        (StreamChannels.siteChannelID eq channelId)
            }.firstOrNull()

            fun getOrInsert(site: DBSite, channelId: String, username: String? = null): StreamChannel = transaction {
                val existing = getChannel(site, channelId)
                existing ?: new {
                    this.site = site
                    this.siteChannelID = channelId
                    if(username != null) this.lastKnownUsername = username
                }
            }

            @WithinExposedContext
            fun insertApiChannel(site: DBSite, channelId: String, username: String): StreamChannel = new {
                this.site = site
                this.siteChannelID= channelId
                this.lastKnownUsername = username
                this.apiUse = true
                this.lastApiUsage = DateTime.now()
            }

            fun getActive(op: SqlExpressionBuilder.()-> Op<Boolean>) = find(op)
                .filter { chan -> chan.apiUse || !chan.targets.empty() }
        }
    }

    object Targets : IntIdTable() {
        val discordClient = integer("target_discord_client").default(1)
        val streamChannel = reference("assoc_stream_channel", StreamChannels, ReferenceOption.CASCADE)
        val discordChannel = reference("discord_channel", DiscordObjects.Channels, ReferenceOption.CASCADE)
        val tracker = reference("discord_user_tracked", DiscordObjects.Users, ReferenceOption.CASCADE)
    }

    class Target(id: EntityID<Int>) : IntEntity(id) {
        var discordClient by Targets.discordClient
        var streamChannel by StreamChannel referencedOn Targets.streamChannel
        var discordChannel by DiscordObjects.Channel referencedOn Targets.discordChannel
        var tracker by DiscordObjects.User referencedOn Targets.tracker

        companion object : IntEntityClass<Target>(Targets) {

            // get target with same discord channel and streaming channel id
            @WithinExposedContext
            fun getForChannel(clientId: Int, discordChan: Snowflake, site: DBSite, channelId: String) = Target.wrapRows(
                Targets
                    .innerJoin(StreamChannels)
                    .innerJoin(DiscordObjects.Channels).select {
                        StreamChannels.site eq site and
                                (Targets.discordClient eq clientId) and
                                (StreamChannels.siteChannelID eq  channelId) and
                                (DiscordObjects.Channels.channelID eq discordChan.asLong())
                    }
            ).firstOrNull()

            // get target
            @WithinExposedContext
            fun getForServer(clientId: Int, guildId: Long, site: DBSite, accountId: String) = wrapRows(
                Targets
                    .innerJoin(StreamChannels)
                    .innerJoin(DiscordObjects.Channels)
                    .innerJoin(DiscordObjects.Guilds).select {
                        StreamChannels.site eq site and
                                (Targets.discordClient eq clientId) and
                                (StreamChannels.siteChannelID eq accountId) and
                                (DiscordObjects.Guilds.guildID eq guildId)
                    }
            ).firstOrNull()
        }
    }

    object Mentions : IdTable<Int>() {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val streamChannel = reference("assoc_stream", StreamChannels, ReferenceOption.CASCADE)
        val guild = reference("assoc_guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val mentionRole = long("discord_mention_role_id").nullable()
        var mentionRoleMember = long("discord_mention_role_membership").nullable()
        var mentionRoleUploads = long("discord_mention_role_uploads").nullable()
        val mentionText = text("discord_mention_text").nullable()
        val lastMention = datetime("last_role_mention_time").nullable()

        override val primaryKey = PrimaryKey(streamChannel, guild)
    }

    class Mention(id: EntityID<Int>) : IntEntity(id) {
        var stream by StreamChannel referencedOn Mentions.streamChannel
        var guild by DiscordObjects.Guild referencedOn Mentions.guild
        var mentionRole by Mentions.mentionRole
        var mentionRoleMember by Mentions.mentionRoleMember
        var mentionRoleUploads by Mentions.mentionRoleUploads
        var mentionText by Mentions.mentionText
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