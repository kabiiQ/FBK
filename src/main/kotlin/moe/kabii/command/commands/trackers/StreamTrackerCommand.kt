package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.StreamingTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.YoutubeTarget
import moe.kabii.trackers.videos.StreamErr
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import org.jetbrains.exposed.sql.transactions.transaction

object StreamTrackerCommand : TrackerCommand {
    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        val streamTarget = requireNotNull(target.site as? StreamingTarget) { "Invalid target arguments provided to StreamTrackerCommand" }
        val site = streamTarget.dbSite

        // make sure feature is enabled or this channel is private
        origin.channelFeatureVerify(streamTarget.channelFeature, streamTarget.featureName, allowOverride = false)

        // validate stream is real and get service ID
        val streamInfo = when(val lookup = streamTarget.getChannel(target.identifier)) {
            is Ok -> lookup.value
            is Err -> {
                val error = when (lookup.value) {
                    is StreamErr.NotFound -> {
                        val ytErr = if(streamTarget is YoutubeTarget) " For YouTube channels, ensure that you are using the 24-digit channnel ID." else ""
                        "Unable to find **${streamTarget.full}** stream **${target.identifier}**.$ytErr"
                    }
                    is StreamErr.IO -> "Error tracking stream. Possible **${streamTarget.full}** API issue."
                }
                origin.ereply(Embeds.error(error)).awaitSingle()
                return
            }
        }
        val streamId = streamInfo.accountId

        // get db 'target' object if it exists
        val dbTarget = transaction {
            TrackedStreams.Target.getForChannel(origin.client.clientId, origin.chan.id, site, streamId)
        }

        // already tracked. otherwise we'll create the target
        if(dbTarget != null) {
            origin.ereply(Embeds.error("**${streamInfo.displayName}** is already tracked.")).awaitSingle()
            return
        }

        // get the db 'channel' object or create if this is a new stream channel
        val dbChannel = TrackedStreams.StreamChannel.getOrInsert(site, streamId, streamInfo.displayName)

        propagateTransaction {
            TrackedStreams.Target.new { // record the track in db
                this.discordClient = origin.client.clientId
                this.streamChannel = dbChannel
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
            }
        }

        origin.ireply(Embeds.fbk("Now tracking **[${streamInfo.displayName}](${streamInfo.url})** on **${streamTarget.full}**!")).awaitSingle()
        TargetSuggestionGenerator.updateTargets(origin.client.clientId, origin.chan.id.asLong())

        // side-effects for prompt data maintenance
        try {
            val callback = streamTarget.onTrack
            if(callback != null) {
                propagateTransaction {
                    callback(origin, dbChannel)
                }
            }
        } catch(e: Exception) {
            LOG.warn("Error getting initial update for StreamChannel: ${e.message}")
            LOG.info(e.stackTraceString)
        }
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments) {
        // get stream info from username the user provides
        val streamTarget = requireNotNull(target.site as? StreamingTarget) { "Invalid target arguments provided to StreamTrackerCommand" }
        val site = streamTarget.dbSite
        val streamInfo = streamTarget.getChannel(target.identifier).orNull()

        if(streamInfo == null) {
            origin.ereply(Embeds.error("Unable to find **${streamTarget.full}** stream **${target.identifier}**.")).awaitSingle()
            return
        }
        val streamId = streamInfo.accountId

        propagateTransaction {
            // check db if stream is tracked in this location
            val dbTarget = TrackedStreams.Target.getForChannel(origin.client.clientId, origin.chan.id, site, streamId)
            if(dbTarget == null) {
                origin.ereply(Embeds.error("**${streamInfo.displayName}** is not currently tracked in this channel.")).awaitSingle()
                return@propagateTransaction
            }
            // user can untrack stream if they tracked it or are channel moderator
            if (
                origin.isPM
                        || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
                        || origin.author.id.asLong() == dbTarget.tracker.userID
            ) {
                dbTarget.delete()
                origin.ireply(Embeds.fbk("No longer tracking **${streamInfo.displayName}**.")).awaitSingle()
                TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
            } else {
                val tracker = origin.chan.client
                    .getUserById(dbTarget.tracker.userID.snowflake).tryAwait().orNull()
                    ?.username ?: "invalid-user"
                origin.ereply(Embeds.error("You may not untrack **${streamInfo.displayName}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission).")).awaitSingle()
            }
        }
    }
}