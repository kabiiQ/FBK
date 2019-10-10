package moe.kabii.discord.command.commands.trackers

import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.ListInfo
import moe.kabii.data.mongodb.MediaSite
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.DiscordParameters
import moe.kabii.discord.command.commands.trackers.twitch.TwitchTrackerCommand

private enum class Action { TRACK, UNTRACK }

private enum class TargetMatch(
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
        "twitch", "stream", "twitch.tv", "ttv"
    )
}

sealed class TrackerTarget
class TargetMediaList(val list: ListInfo) : TrackerTarget()
class TargetTwitchStream(val stream: String) : TrackerTarget()

interface Tracker<in T: TrackerTarget> {
    suspend fun track(origin: DiscordParameters, target: T)
    suspend fun untrack(origin: DiscordParameters, target: T)
}

private suspend fun trackCommand(param: DiscordParameters, action: Action) {
    val urlMatch = TargetMatch.values()
        .mapNotNull { target ->
            target.url.find(param.noCmd)?.to(target)
        }.firstOrNull()
    val channelFeature by lazy { // check if one feature is enabled in this channel
        param.guild // any of these might be null, then the condition is not going to be true
            ?.run { GuildConfigurations.getOrCreateGuild(id.asLong()) }
            ?.run { options.featureChannels[param.chan.id.asLong()] }
            ?.run {
                if(booleanArrayOf(twitchChannel, animeChannel).count(true::equals) == 1) {
                    when {
                        twitchChannel -> TargetMatch.TWITCH
                        animeChannel -> TargetMatch.MAL
                        else -> null
                    }
                } else null
            }
    }

    val (target, listID) = when {
        // if url, use that to determine target
        urlMatch != null -> {
            urlMatch.second to urlMatch.first.groups[1]?.value!!
        }
        // else, if only one feature is enabled, use that target
        param.args.size < 2 && channelFeature != null -> {
            // tra1ck <username>
            if(param.args.isEmpty()) {
                param.usage("No username provided.", "track/untrack (site name, defaulting to ${channelFeature!!.name}) <site username>").block()
                return
            }
            channelFeature to param.args[0]
        }
        // else, user must provide target name
        else -> {
            if(param.args.size < 2) {
                param.usage("The **track** command is used to follow a supported content source in this channel. If there is a single content source enabled in this channel I will automatically use that one, otherwise you will need to specify the site.", "track/untrack <site name> <username>").block()
                return
            }
            val siteArg = param.args[0].toLowerCase()
            val match = TargetMatch.values().firstOrNull { target ->
                target.alias.contains(siteArg)
            }
            if(match != null) match to param.args[1]
            else {
                param.usage("Unknown track target **${param.args[0]}.**", "track/untrack <site name> <username>").block()
                return
            }
        }
    }
    when(target) {
        TargetMatch.MAL -> when(action) {
            Action.TRACK -> MediaTrackerCommand.track(param, TargetMediaList(ListInfo(MediaSite.MAL, listID)))
            Action.UNTRACK -> MediaTrackerCommand.untrack(param, TargetMediaList(ListInfo(MediaSite.MAL, listID)))
        }
        TargetMatch.KITSU -> when(action) {
            Action.TRACK -> MediaTrackerCommand.track(param, TargetMediaList(ListInfo(MediaSite.KITSU, listID)))
            Action.UNTRACK -> MediaTrackerCommand.untrack(param, TargetMediaList(ListInfo(MediaSite.KITSU, listID)))
        }
        TargetMatch.TWITCH -> when(action) {
            Action.TRACK -> TwitchTrackerCommand.track(param, TargetTwitchStream(listID))
            Action.UNTRACK -> TwitchTrackerCommand.untrack(param, TargetTwitchStream(listID))
        }
    }
}

object TrackerCommandBase : CommandContainer {
    object TrackCommandBase : Command("track") {
        init {
            discord {
                trackCommand(this, Action.TRACK)
            }
        }
    }

    object UntrackCommandBase : Command("untrack") {
        init {
            discord {
                trackCommand(this, Action.UNTRACK)
            }
        }
    }
}