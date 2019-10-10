package moe.kabii.discord.command.commands.trackers.twitch

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.relational.DiscordObjects
import moe.kabii.data.relational.DiscordObjects.Guild.Companion.getOrInsert
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.FeatureDisabledException
import moe.kabii.discord.command.commands.trackers.TargetTwitchStream
import moe.kabii.discord.command.hasPermissions
import moe.kabii.helix.EmptyObject
import moe.kabii.helix.HelixIOErr
import moe.kabii.helix.TwitchHelix
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import org.jetbrains.exposed.sql.transactions.transaction

object TwitchTrackerCommand {
    fun track(origin: DiscordParameters, target: TargetTwitchStream) {
        val config = origin.guild?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
        if(config != null) {
            val features = config.options.featureChannels[origin.chan.id.asLong()]
            if (features == null || !features.twitchChannel) throw FeatureDisabledException("twitch")
        }

        // validate twitch stream is real
        val stream = when(val lookup = TwitchHelix.getUser(target.stream)) {
            is Ok -> lookup.value
            is Err -> {
                when (lookup.value) {
                    is EmptyObject -> origin.error("Unable to find Twitch stream **${target.stream}.**").block()
                    is HelixIOErr -> origin.error("Error tracking stream. Possible Twitch outage?").block()
                }
                return
            }
        }
        val twitchID = stream.id.toLong()

        transaction {
            val trackedStream = TrackedStreams.Stream
                .find { TrackedStreams.Streams.stream_id eq twitchID }
                .elementAtOrElse(0) { _ ->
                    TrackedStreams.Stream.new { this.stream = twitchID } // stream is not tracked at all, track it
                }

            val discordChan = origin.chan.id.asLong()
            val streamTargets = trackedStream.targets
            val find = streamTargets.find { trackedTarget -> trackedTarget.channel == discordChan }
            if (find == null) { // if stream is not tracked in this channel
                val guildID = origin.target.id.asLong()
                TrackedStreams.Target.new {
                    this.stream = trackedStream
                    this.channel = discordChan
                    this.tracker = DiscordObjects.User.getOrInsert(origin.author.id.asLong())
                    this.guild = origin.guild?.id?.asLong()?.run(DiscordObjects.Guild.Companion::getOrInsert)
                }
                origin.embed("Now tracking **${stream.display_name}**!")
            } else {
                origin.error("**${stream.display_name}** is already tracked.")
            }.block()
        }
    }

    fun untrack(origin: DiscordParameters, target: TargetTwitchStream) {
        val config = origin.guild?.let { guild -> GuildConfigurations.getOrCreateGuild(guild.id.asLong()) }

        // get twitch id from username the user provides
        val twitchStream = TwitchHelix.getUser(target.stream).orNull()
        if(twitchStream == null) {
            origin.error("Unable to find Twitch stream **${target.stream}**.").block()
            return
        }

        // untrack the stream from this location
        val twitchID = twitchStream.id.toLong()
        transaction {
            val trackedStream = TrackedStreams.Stream
                .find { TrackedStreams.Streams.stream_id eq twitchID }
                .singleOrNull()
            if(trackedStream != null) { // this stream is tracked in some channel
                val trackedTarget = trackedStream.targets.find { trackedTarget -> trackedTarget.channel == origin.chan.id.asLong() }
                if(trackedTarget != null) {
                    if(origin.isPM
                        || origin.author.id.asLong() == trackedTarget.tracker.userID
                        || origin.event.member.get().hasPermissions(Permission.MANAGE_MESSAGES)) {
                        trackedTarget.delete()
                        origin.embed("No longer tracking **${twitchStream.display_name}**.").block()

                        if(config != null) {
                            // todo call clean twitch roles
                        }
                        return@transaction
                    } else {
                        val tracker = origin.chan.client.getUserById(trackedTarget.tracker.userID.snowflake).tryBlock().orNull()?.username ?: "invalid-user"
                        origin.error("You may not untrack **${twitchStream.display_name}** unless you are $tracker or a server moderator.").block()
                        return@transaction
                    }
                }
            }
            origin.error("**${twitchStream.display_name}** is not currently being tracked.").block()
        }
    }
}