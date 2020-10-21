package moe.kabii.command.commands.trackers

import discord4j.common.util.Snowflake
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.FeatureDisabledException
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object StreamTrackerCommand {
    suspend fun track(origin: DiscordParameters, target: TargetArguments) {
        val streamTarget = requireNotNull(target.site as? StreamingTarget) { "Invalid target arguments provided to StreamTrackerCommand" }
        val site = streamTarget.dbSite

        // make sure feature is enabled or this channel is private
        if(origin.guild != null) {
            val config = GuildConfigurations.getOrCreateGuild(origin.guild.id.asLong())
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if(features == null || !features.twitchChannel) throw FeatureDisabledException("streams", origin)
        } // else this is PM, allow

        // validate stream is real and get service ID
        val streamInfo = when(val lookup = streamTarget.getChannel(target.identifier)) {
            is Ok -> lookup.value
            is Err -> {
                val error = when (lookup.value) {
                    is StreamErr.NotFound -> "Unable to find **${streamTarget.full}** stream **${target.identifier}**."
                    is StreamErr.IO -> "Error tracking stream. Possible **${streamTarget.full}** API issue."
                }
                origin.error(error).awaitSingle()
                return
            }
        }
        val streamId = streamInfo.accountId

        // get db 'target' object if it exists
        val dbTarget = getDBTarget(origin.chan.id, site, streamId)

        // already tracked. otherwise we'll create the target
        if(dbTarget != null) {
            origin.error("**${streamInfo.displayName}** is already tracked.").awaitSingle()
            return
        }

        val dbChannel = transaction { // get the db 'channel' object or create if this is a new stream channel
            TrackedStreams.StreamChannel.find { TrackedStreams.StreamChannels.siteChannelID eq streamId }
                .elementAtOrElse(0) { _ ->
                    TrackedStreams.StreamChannel.new {
                        this.site = site
                        this.siteChannelID = streamId
                    }
                }
        }
        transaction {
            TrackedStreams.Target.new { // record the track in db
                this.streamChannel = dbChannel
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }
        origin.embed("Now tracking **${streamInfo.displayName}** on **${streamTarget.full}**!").awaitSingle()
    }

    suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        // get stream info from username the user provides
        val streamTarget = requireNotNull(target.site as? StreamingTarget) { "Invalid target arguments provided to StreamTrackerCommand" }
        val site = streamTarget.dbSite
        val streamInfo = streamTarget.getChannel(target.identifier).orNull()

        if(streamInfo == null) {
            origin.error("Unable to find **${streamTarget.full}** stream **${target.identifier}**.").awaitSingle()
            return
        }
        val streamId = streamInfo.accountId

        // check db if stream is tracked in this location
        val dbTarget = getDBTarget(origin.chan.id, site, streamId)
        if(dbTarget == null) {
            origin.error("**${streamInfo.displayName}** is not currently tracked in this channel.").awaitSingle()
            return
        }
        // user can untrack stream if they tracked it or are channel moderator
        if (
            origin.isPM
                    || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
                    || origin.author.id.asLong() == transaction { dbTarget.tracker.userID }
        ) {

            if(origin.guild != null) {
                val oldMentionRole = transaction {
                    TrackedStreams.Mention.getMentionsFor(origin.guild.id, streamId)
                        .singleOrNull(TrackedStreams.Mention::isAutomaticSet) // <- we can delete role if this stream had one and it is not in use (single) and we created it (automatic)
                }
                if (oldMentionRole != null) {
                    origin.guild.getRoleById(oldMentionRole.mentionRole.snowflake)
                        .flatMap { role -> role.delete("Stream untracked.") }
                        .success().awaitSingle()
                }
            }
            transaction {
                dbTarget.delete()
            }
            origin.embed("No longer tracking **${streamInfo.displayName}**.").awaitSingle()
        } else {
            val tracker = origin.chan.client
                .getUserById(dbTarget.tracker.userID.snowflake).tryAwait().orNull()
                ?.username ?: "invalid-user"
            origin.error("You may not untrack **${streamInfo.displayName}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission).").awaitSingle()
        }
    }

    // get target with same discord channel and streaming channel id
    fun getDBTarget(discordChan: Snowflake, site: TrackedStreams.DBSite, channelId: String): TrackedStreams.Target? = transaction {
        TrackedStreams.Target.wrapRows(
            TrackedStreams.Targets
                .innerJoin(TrackedStreams.StreamChannels)
                .innerJoin(DiscordObjects.Channels).select {
                    TrackedStreams.StreamChannels.site eq site and
                            (TrackedStreams.StreamChannels.siteChannelID eq channelId) and
                            (DiscordObjects.Channels.channelID eq discordChan.asLong())
            }
        ).firstOrNull()
    }
}