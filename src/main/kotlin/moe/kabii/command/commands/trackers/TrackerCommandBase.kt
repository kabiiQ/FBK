package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.BotSendMessageException
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.ChatCommandArguments
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verifyBotAccess
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.*

private enum class Action { TRACK, UNTRACK }

interface TrackerCommand {
    suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?)
    suspend fun untrack(origin: DiscordParameters, target: TargetArguments)
}

object TrackerCommandBase : CommandContainer {
    object TrackCommandBase : Command("track") {
        override val wikiPath = "Livestream-Tracker"

        init {
            chat {
                trackCommand(this, Action.TRACK)
            }
        }
    }

    object UntrackCommandBase : Command("untrack") {
        override val wikiPath: String? = null // undocumented 'base' command

        init {
            autoComplete {
                val channelId = event.interaction.channelId.asLong()
                val siteArg = ChatCommandArguments(event).optInt("site")
                suggest(TargetSuggestionGenerator.getTargets(client.clientId, channelId, value, siteArg))
            }

            chat {
                trackCommand(this, Action.UNTRACK)
            }
        }
    }

    private suspend fun trackCommand(origin: DiscordParameters, action: Action) = with(origin) {
        val features = guild?.id?.asLong()?.run {
            GuildConfigurations.guildConfigurations[GuildTarget(client.clientId, this)]
        }?.options?.featureChannels?.get(chan.id.asLong())

        // limit track command if this is a guild with 'locked' config
        if(guild != null && features != null && features.locked) {
            channelVerify(Permission.MANAGE_MESSAGES)
        }

        val target = args.optInt("site")?.run(TrackerTarget::parseSiteArg)
        when(val findTarget = TargetArguments.parseFor(this, args.string("username"), target)) {
            is Ok -> {
                val targetArgs = findTarget.value
                val tracker = when(targetArgs.site) {
                    is StreamingTarget -> StreamTrackerCommand
                    is AnimeTarget -> MediaTrackerCommand
                    is TwitterTarget -> TwitterTrackerCommand
                }
                when(action) {
                    Action.TRACK -> tracker.track(this, targetArgs, features)
                    Action.UNTRACK -> tracker.untrack(this, targetArgs)
                }
            }
            is Err -> {
                ereply(Embeds.error("Unable to track: ${findTarget.value}")).awaitSingle()
            }
        }
    }

    suspend fun sendTrackerTestMessage(origin: DiscordParameters) {
        val success = origin.chan.verifyBotAccess("Verifying my permission to send embeds in this channel.")
        if(!success) {
            origin
                .ereply(Embeds.error("**Permission test failed.**\n\nPlease make sure I have permission to send **\"Send Messages\"** and **\"Embed Links\"** in this channel, or tracker messages will fail to send later!\n\nI am able to send /command responses (such as this one) regardless of permissions, but sending tracker updates will require me to have basic permissions in this channel."))
                .awaitSingle()
            throw BotSendMessageException("Tracker permission test failed", origin.chan.id.asLong())
        }
    }
} 