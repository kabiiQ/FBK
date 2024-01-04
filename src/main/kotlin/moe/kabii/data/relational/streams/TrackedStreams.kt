package moe.kabii.data.relational.streams

import discord4j.common.util.Snowflake
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.trackers.*
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.util.extensions.RequiresExposedContext
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

        SPACES(YoutubeTarget),

        KICK(KickTarget)
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

            @RequiresExposedContext
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

            @RequiresExposedContext
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
            @RequiresExposedContext
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
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val target = reference("mention_assoc_target", Targets, ReferenceOption.CASCADE)
        val mentionRole = long("discord_mention_role_id").nullable()
        val mentionRoleMember = long("discord_mention_role_membership").nullable()
        val mentionRoleUpcoming = long("discord_mention_role_upcoming").nullable()
        val mentionRoleCreation = long("discord_mention_role_creation").nullable()
        val mentionRoleUploads = long("discord_mention_role_uploads").nullable()
        val mentionText = text("discord_mention_text", eagerLoading = true).nullable()
        val mentionTextMember = text("discord_mention_text_membership", eagerLoading = true).nullable()
        val lastMention = datetime("last_role_mention_time").nullable()

        init {
            index(isUnique = true, target)
        }
    }

    class TargetMention(id: EntityID<Int>) : IntEntity(id) {
        var mentionRole by TargetMentions.mentionRole
        var mentionRoleMember by TargetMentions.mentionRoleMember
        var mentionRoleUploads by TargetMentions.mentionRoleUploads
        var mentionRoleUpcoming by TargetMentions.mentionRoleUpcoming
        var mentionRoleCreation by TargetMentions.mentionRoleCreation
        var mentionText by TargetMentions.mentionText
        var mentionTextMember by TargetMentions.mentionTextMember
        var lastMention by TargetMentions.lastMention

        var target by Target referencedOn TargetMentions.target

        companion object : IntEntityClass<TargetMention>(TargetMentions)
    }

    object DiscordEvents : IdTable<Int>() {
        override val id = integer("id").autoIncrement().entityId().uniqueIndex()
        val client = integer("discord_client")
        val guild = reference("discord_guild", DiscordObjects.Guilds, ReferenceOption.CASCADE)
        val stream = reference("channel", StreamChannels, ReferenceOption.CASCADE)
        val event = long("discord_event_id").uniqueIndex()
        val startTime = datetime("scheduled_start_time")
        val endTime = datetime("scheduled_end_time")
        val title = text("event_saved_title", eagerLoading = true)
        val yt = integer("yt_video_id")
        val valid = bool("valid_event").default(true)

        override val primaryKey = PrimaryKey(guild, stream, yt)
    }

    class DiscordEvent(id: EntityID<Int>) : IntEntity(id) {
        var client by DiscordEvents.client
        var guild by DiscordObjects.Guild referencedOn DiscordEvents.guild
        var channel by StreamChannel referencedOn DiscordEvents.stream
        var event by DiscordEvents.event
        var startTime by DiscordEvents.startTime
        var endTime by DiscordEvents.endTime
        var title by DiscordEvents.title
        var valid by DiscordEvents.valid

        /**
         * Database ID of an associated YouTube video, -1 if no video is associated
         */
        var yt by DiscordEvents.yt

        companion object : IntEntityClass<DiscordEvent>(DiscordEvents) {
            operator fun get(target: StreamWatcher.TrackedTarget, ytVideo: YoutubeVideo?) = DiscordEvent.wrapRows(
                DiscordEvents
                    .innerJoin(DiscordObjects.Guilds)
                    .select {
                        DiscordObjects.Guilds.guildID eq target.discordGuild!!.asLong() and
                                (DiscordEvents.stream eq target.dbStream) and
                                (DiscordEvents.yt eq (ytVideo?.id?.value?.toInt() ?: -1))
                    }
            ).firstOrNull()
        }
    }
}