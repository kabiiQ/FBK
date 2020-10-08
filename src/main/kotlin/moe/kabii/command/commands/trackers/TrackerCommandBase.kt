package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.discord.trackers.AnimeTarget
import moe.kabii.discord.trackers.StreamingTarget
import moe.kabii.discord.trackers.TargetArguments
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok

private enum class Action { TRACK, UNTRACK }

object TrackerCommandBase : CommandContainer {
    object TrackCommandBase : Command("track") {
        override val wikiPath: String? = null // undocumented 'base' command

        init {
            discord {
                if(args.isEmpty()) {
                    val modErr = if(guild != null) {
                        if(member.hasPermissions(guildChan, Permission.MANAGE_CHANNELS)) {
                            " As a channel moderator, you can allow accounts to be tracked from [supported websites](https://github.com/kabiiQ/FBK/wiki/Configuration-Commands#available-options-in-features)"
                        } else ""
                    } else ""
                    usage("The **track** command is used to follow a supported account in this channel.$modErr", "track (site name) <account name/id>").awaitSingle()
                    return@discord
                }
                trackCommand(this, Action.TRACK)
            }
        }
    }

    object UntrackCommandBase : Command("untrack") {
        override val wikiPath: String? = null // undocumented 'base' command

        init {
            discord {
                if(args.isEmpty()) {
                    usage("The **untrack** command is used to unfollow a tracked account in this channel.", "untrack (site name) <account name/id>").awaitSingle()
                    return@discord
                }
                trackCommand(this, Action.UNTRACK)
            }
        }
    }

    private suspend fun trackCommand(origin: DiscordParameters, action: Action) = with(origin) {
        // args is known to be contain at least 1 arg here

        when(val trackTarget = TargetArguments.parseFor(this, args)) {
            is Ok -> {
                val targetArgs = trackTarget.value
                when(targetArgs.site) {
                    is StreamingTarget -> when(action) {
                        Action.TRACK -> StreamTrackerCommand.track(this, targetArgs)
                        Action.UNTRACK -> StreamTrackerCommand.untrack(this, targetArgs)
                    }
                    is AnimeTarget -> when(action) {
                        Action.TRACK -> MediaTrackerCommand.track(this, targetArgs)
                        Action.UNTRACK -> MediaTrackerCommand.untrack(this, targetArgs)
                    }
                }
            }
            is Err -> {
                val err = trackTarget.value
                usage(err, "${command.baseName} (site name) <account name/ID>").awaitSingle()
            }
        }
    }
} 