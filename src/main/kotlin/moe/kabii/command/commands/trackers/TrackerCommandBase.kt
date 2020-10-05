package moe.kabii.command.commands.trackers

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.hasPermissions
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.ListInfo
import moe.kabii.data.mongodb.MediaSite
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.rusty.*

private enum class Action { TRACK, UNTRACK }

enum class TargetMatch(
    val url: Regex,
    vararg val alias: String

) {
    MAL(
        Regex("myanimelist.net/(animelist|mangalist|profile)/[a-zA-Z0-9_]{2,16}"),
        "mal", "myanimelist", "myanimelist.net", "animelist", "mangalist"
    ),
    KITSU(
        Regex("kitsu.io/users/[a-zA-Z0-9_]{3,20}"),
        "kitsu", "kitsu.io"
    ),
    TWITCH(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})"),
        "twitch", "twitch.tv", "ttv"
    );

    companion object {
        fun parseStreamSite(name: String): TrackedStreams.DBSite? {
            val streams = arrayOf(TWITCH)
            val match = streams.find { pattern -> pattern.alias.find(name.toLowerCase()::equals) != null }
            return when (match) {
                TWITCH -> TrackedStreams.DBSite.TWITCH
                else -> null
            }
        }
    }
}

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

        // get the channel features, if they exist. PMs do not require trackers to be enabled
        // thus, a URL or site name must be specified if used in PMs
        val features = if(guild != null) {
            val features = GuildConfigurations.getOrCreateGuild(guild.id.asLong()).options.featureChannels[guildChan.id.asLong()]

            if(features == null) {
                // if this is a guild but the channel never had any features enabled to begin with
                error("There are no website trackers enabled in **#${guildChan.name}**.").awaitSingle()
                return@with
            } else features
        } else null

        val trackTarget = TrackCommandUtil.parseTrackTarget(this, args)
        when(trackTarget) {
            is Ok -> {
                val targetArgs = trackTarget.value
                when(targetArgs.site) {
                    TargetMatch.MAL -> when(action) {
                        Action.TRACK -> MediaTrackerCommand.track(this, ListInfo(MediaSite.MAL, targetArgs.accountId))
                        Action.UNTRACK -> MediaTrackerCommand.untrack(this, ListInfo(MediaSite.MAL, targetArgs.accountId))
                    }
                    TargetMatch.KITSU -> when(action) {
                        Action.TRACK -> MediaTrackerCommand.track(this, ListInfo(MediaSite.KITSU, targetArgs.accountId))
                        Action.UNTRACK -> MediaTrackerCommand.untrack(this, ListInfo(MediaSite.KITSU, targetArgs.accountId))
                    }
                    TargetMatch.TWITCH -> when(action) {
                        Action.TRACK -> TwitchTrackerCommand.track(this, targetArgs.accountId)
                        Action.UNTRACK -> TwitchTrackerCommand.untrack(this, targetArgs.accountId)
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