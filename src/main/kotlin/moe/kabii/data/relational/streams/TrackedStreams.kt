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

        init {
            index(customIndexName = "targets_unique_per_channel", isUnique = true, discordClient, streamChannel, discordChannel)
        }
    }

    class Target(id: EntityID<Int>) : IntEntity(id) {
        var discordClient by Targets.discordClient
        var streamChannel by StreamChannel referencedOn Targets.streamChannel
        var discordChannel by DiscordObjects.Channel referencedOn Targets.discordChannel
        var tracker by DiscordObjects.User referencedOn Targets.tracker

        val mention by TargetMention referrersOn TargetMentions.target

        fun mention() = this.mention.firstOrNull()

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
        }
    }

    object TargetMentions : IdTable<Int>() {
        // extension of Target. fk, pk = target
        val mentionRole = long("discord_mention_role_id").nullable()
        var mentionRoleMember = long("discord_mention_role_membership").nullable()
        var mentionRoleUploads = long("discord_mention_role_uploads").nullable()
        val mentionText = text("discord_mention_text").nullable()
        val lastMention = datetime("last_role_mention_time").nullable()

        val target = reference("mention_assoc_target", Targets, ReferenceOption.CASCADE)
        override val id = target
        override val primaryKey = PrimaryKey(id)
    }

    class TargetMention(id: EntityID<Int>) : IntEntity(id) {
        var mentionRole by TargetMentions.mentionRole
        var mentionRoleMember by TargetMentions.mentionRoleMember
        var mentionRoleUploads by TargetMentions.mentionRoleUploads
        var mentionText by TargetMentions.mentionText
        var lastMention by TargetMentions.lastMention

        var target by Target referencedOn TargetMentions.target

        companion object : IntEntityClass<TargetMention>(TargetMentions)
    }
}