package moe.kabii.discord.command.commands.trackers

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.FeatureDisabledException
import moe.kabii.discord.command.hasPermissions
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.util.RoleUtil
import moe.kabii.rusty.*
import moe.kabii.structure.snowflake
import moe.kabii.structure.success
import moe.kabii.structure.tryBlock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object StreamTrackerCommand : Tracker<TargetStream> {
    override suspend fun track(origin: DiscordParameters, target: TargetStream) {
        // make sure feature is enabled or this channel is private
        val config = origin.guild?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
        if(config != null) {
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if (features == null || !features.twitchChannel) throw FeatureDisabledException("stream", origin)
        }

        // validate stream is real and get id
        val channelID = origin.chan.id.asLong()
        val target = target.stream
        val parser = target.site.parser

        val stream = when(val lookup = parser.getUser(target.id)) {
            is Ok -> lookup.value
            is Err -> {
                val error = when(lookup.value) {
                    is StreamErr.NotFound -> "Unable to find **${target.site.full}** stream **${target.id}**."
                    is StreamErr.IO -> "Error tracking stream. Possible **${target.site.full}** API issue."
                }
                origin.error(error).block()
                return
            }
        }
        val streamID = stream.userID

        // get db 'target' object if it exists
        val dbTarget = getDBTarget(origin.chan.id, TrackedStreams.StreamInfo(stream.parser.site, stream.userID))

        // already tracked. otherwise we'll create the target
        if(dbTarget != null) {
            origin.error("**${stream.displayName}** is already tracked.").block()
            return
        }

        val guildID = origin.target.id.asLong()
        val dbChannel = transaction { // get the db 'channel' object or create if this is a new stream channel
            TrackedStreams.Channel.find { TrackedStreams.Channels.channelID eq streamID }
                .elementAtOrElse(0) { _ ->
                    TrackedStreams.Channel.new {
                        this.site = target.site
                        this.channelID = streamID
                    }
                }
        }
        transaction {
            TrackedStreams.Target.new { // record the track in db
                this.channelID = dbChannel
                this.discordChannel = origin.target.id.asLong()
                this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
                this.guild = origin.guild?.run { DiscordObjects.Guild.getOrInsert(id.asLong()) }
                this.mention = null
            }
        }
        origin.embed("Now tracking **${stream.displayName}**!").block()
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetStream) {
        // get stream info from username the user provides
        val target = target.stream
        val stream = target.site.parser.getUser(target.id).orNull()

        if(stream == null) {
            origin.error("Unable to find **${target.site.full}** stream **${target.id}**.").block()
            return
        }

        // check db if stream is tracked in this location
        val dbTarget = getDBTarget(origin.chan.id, TrackedStreams.StreamInfo(stream.parser.site, stream.userID))
        if(dbTarget == null) {
            origin.error("**${stream.displayName}** is not currently tracked in this channel.").block()
            return
        }
        // user can untrack stream if they tracked it or are channel moderator
        transaction {
            if (
                origin.isPM
                || origin.author.id.asLong() == dbTarget.tracker.userID
                || origin.member.hasPermissions(Permission.MANAGE_MESSAGES)
            ) {
                val mentionRole = dbTarget.mention
                dbTarget.delete()
                origin.embed("No longer tracking **${stream.displayName}**.").block()

                // can delete associated mention role
                if(mentionRole != null) {
                    origin.guild!!.getRoleById(mentionRole.snowflake)
                        .flatMap { role -> role.delete("Stream untracked.") }
                        .tryBlock(false).orNull()
                }
                Unit
            } else {
                val tracker = origin.chan.client
                    .getUserById(dbTarget.tracker.userID.snowflake).tryBlock(false).orNull()
                    ?.username ?: "invalid-user"
                origin.error("You may not untrack **${stream.displayName}** unless you tracked this stream (**$tracker**) or are a channel moderator (Manage Messages permission).").block()
            }
        }
    }

    // get target with same discord channel and streaming channel id
    fun getDBTarget(discordChan: Snowflake, stream: TrackedStreams.StreamInfo): TrackedStreams.Target? = transaction {
        TrackedStreams.Target.wrapRows(
            TrackedStreams.Targets.innerJoin(TrackedStreams.Channels).select {
                TrackedStreams.Channels.site eq stream.site and
                        (TrackedStreams.Channels.channelID eq stream.id)
                        (TrackedStreams.Targets.discordChannel eq discordChan.asLong())
            }
        ).firstOrNull()
    }
}