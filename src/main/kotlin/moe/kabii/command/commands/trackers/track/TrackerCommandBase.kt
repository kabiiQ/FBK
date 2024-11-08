package moe.kabii.command.commands.trackers.track

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.BotSendMessageException
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.commands.trackers.util.GlobalTrackSuggestionGenerator
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
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
    suspend fun untrack(origin: DiscordParameters, target: TargetArguments, moveTo: GuildMessageChannel?)
}

object TrackerCommandBase : CommandContainer {
    object TrackCommandBase : Command("track") {
        override val wikiPath = "Livestream-Tracker"

        init {
            autoComplete {
                val siteArg = ChatCommandArguments(event).optInt("site")
                val feeds = GlobalTrackSuggestionGenerator.suggestFeeds(value, siteArg)
                suggest(feeds)
            }

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
                val feeds = TargetSuggestionGenerator.getTargets(client.clientId, channelId, value, siteArg)
                suggest(feeds)
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
                // Check if this is an untrack requested to be 'moved' to a different channel
                val moveTo = args
                    .optChannel("moveto", GuildMessageChannel::class)
                    ?.awaitSingle()

                val targetArgs = findTarget.value
                val tracker = when(targetArgs.site) {
                    is StreamingTarget -> StreamTrackerCommand
                    is AnimeTarget -> MediaTrackerCommand
                    is SocialTarget -> PostsTrackerCommand
                    is YoutubeVideoTarget -> YoutubeVideoUntrackCommand
                }
                when(action) {
                    Action.TRACK -> tracker.track(this, targetArgs, features)
                    Action.UNTRACK -> tracker.untrack(this, targetArgs, moveTo)
                }
            }
            is Err -> {
                ereply(Embeds.error("Unable to track: ${findTarget.value}")).awaitSingle()
            }
        }
    }

    suspend fun sendTrackerTestMessage(origin: DiscordParameters, altChannel: GuildMessageChannel? = null) {
        val testChannel = altChannel ?: origin.chan
        val success = testChannel.verifyBotAccess("Verifying my permission to send embeds in this channel.")
        if(!success) {
            origin
                .ereply(Embeds.error("**Permission test failed.**\n\nPlease make sure I have permission to send **\"Send Messages\"** and **\"Embed Links\"** in <#${testChannel.id.asString()}>, or tracker messages will fail to send later!\n\nI am able to send /command responses (such as this one) regardless of permissions, but sending tracker updates will require me to have basic permissions in this channel.\n\nYou may want to use the \"**View Server as Role**\" feature in Discord on FBK's bot role to help understand what the bot can see and do in your server!"))
                .awaitSingle()
            throw BotSendMessageException("Tracker permission test failed", testChannel.id.asLong())
        }
    }
} 