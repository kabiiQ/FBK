package moe.kabii.command.commands.trackers

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.ListInfo
import moe.kabii.data.mongodb.MediaSite
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.types.DiscordParameters

private enum class Action { TRACK, UNTRACK }
private enum class TargetType { ANIME, STREAM }

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
    ),
    MIXER(
        Regex("mixer.tv/([a-zA-Z0-9_]{1,20})"),
        "mixer", "mixer.tv", "mtv"
    );

    companion object {
        fun parseStreamSite(name: String): TrackedStreams.Site? {
            val streams = arrayOf(TWITCH, MIXER)
            val match = streams.find { pattern -> pattern.alias.find(name.toLowerCase()::equals) != null }
            return when (match) {
                TWITCH -> TrackedStreams.Site.TWITCH
                //MIXER -> TrackedStreams.Site.MIXER
                else -> null
            }
        }
    }
}

sealed class TrackerTarget
class TargetMediaList(val list: ListInfo) : TrackerTarget()
class TargetStream(val stream: TrackedStreams.StreamQueryInfo) : TrackerTarget()

interface Tracker<in T: TrackerTarget> {
    suspend fun track(origin: DiscordParameters, target: T)
    suspend fun untrack(origin: DiscordParameters, target: T)
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

    private suspend fun trackCommand(param: DiscordParameters, action: Action) {
        val urlMatch = TargetMatch.values()
            .mapNotNull { target ->
                target.url.find(param.noCmd)?.to(target)
            }.firstOrNull()
        val channelFeature by lazy { // check if only one feature is enabled in channel - otherwise we need to know the target
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
                    param.usage("No username provided.", "track/untrack (site name, defaulting to ${channelFeature!!.name}) <site username>").awaitSingle()
                    return
                }
                channelFeature to param.args[0]
            }
            // else, user must provide target name
            else -> {
                if(param.args.size < 2) {
                    param.usage("The **track** command is used to follow a supported source in this channel. If there is a single source enabled in this channel I will automatically use that one, otherwise you will need to specify the site.", "track/untrack <site name> <username>").awaitSingle()
                    return
                }
                val siteArg = param.args[0].toLowerCase()
                val match = TargetMatch.values().firstOrNull { target ->
                    target.alias.contains(siteArg)
                }
                if(match != null) match to param.args[1]
                else {
                    param.usage("Unknown track target **${param.args[0]}.**", "track/untrack <site name> <username>").awaitSingle()
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
                Action.TRACK -> StreamTrackerCommand.track(param, TargetStream(TrackedStreams.StreamQueryInfo(TrackedStreams.Site.TWITCH, listID)))
                Action.UNTRACK -> StreamTrackerCommand.untrack(param, TargetStream(TrackedStreams.StreamQueryInfo(TrackedStreams.Site.TWITCH, listID)))
            }
            TargetMatch.MIXER -> param.error("Mixer support is not yet implemented. Let @kabii#0001 know if you would use Mixer stream integration so that this feature can be prioritized.")
        }
    }
} 