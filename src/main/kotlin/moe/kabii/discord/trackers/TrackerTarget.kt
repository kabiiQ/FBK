package moe.kabii.discord.trackers

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.discord.trackers.streams.youtube.YoutubeParser
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.extensions.stackTraceString
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

sealed class TrackerTarget(
    val full: String,
    val channelFeature: KProperty1<FeatureChannel, Boolean>,
    val url: List<Regex>,
    vararg val alias: String
)

// streaming targets
data class BasicStreamChannel(val site: StreamingTarget, val accountId: String, val displayName: String, val url: String)

sealed class StreamingTarget(
    val serviceColor: Color,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, channelFeature, url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: TrackedStreams.DBSite

    // return basic info about the stream, primarily just if it exists + account ID needed for DB
    abstract suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr>
    abstract suspend fun getChannelById(id: String): Result<BasicStreamChannel, StreamErr>
}

object TwitchTarget : StreamingTarget(
    TwitchParser.color,
    "Twitch",
    FeatureChannel::twitchChannel,
    listOf(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})")
    ),
    "twitch", "twitch.tv", "ttv"
) {
    override val dbSite
        get() = TrackedStreams.DBSite.TWITCH

    override suspend fun getChannel(id: String) = TwitchParser.getUser(id).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName, ok.url) }
    override suspend fun getChannelById(id: String) = TwitchParser.getUser(id.toLong()).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName, ok.url) }
}

object YoutubeTarget : StreamingTarget(
    YoutubeParser.color,
    "YouTube",
    FeatureChannel::twitchChannel,
    listOf(
        Regex("([a-zA-Z0-9-_]{24})"),
        Regex("youtube.com/channel/([a-zA-Z0-9-_]{24})")
    ),
    "youtube", "yt", "youtube.com", "utube", "ytube"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.YOUTUBE

    override suspend fun getChannel(id: String) = getChannelByUnknown(id)
    override suspend fun getChannelById(id: String) = getChannelByUnknown(id)

    private fun getChannelByUnknown(identifier: String) = try {
        val channel = YoutubeParser.getChannelFromUnknown(identifier)
        if(channel != null) {
            val info = BasicStreamChannel(YoutubeTarget, channel.id, channel.name, channel.url)
            Ok(info)
        } else Err(StreamErr.NotFound)
    } catch(e: Exception) {
        LOG.debug("Error getting YouTube channel: ${e.message}")
        LOG.trace(e.stackTraceString)
        Err(StreamErr.IO)
    }
}

// anime targets
sealed class AnimeTarget(
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, channelFeature, url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: ListSite
}

object MALTarget : AnimeTarget(
    "MyAnimeList",
    FeatureChannel::animeChannel,
    listOf(
        Regex("myanimelist.net/(animelist|mangalist|profile)/[a-zA-Z0-9_]{2,16}")
    ),
    "mal", "myanimelist", "myanimelist.net", "animelist", "mangalist"
) {
    override val dbSite: ListSite
        get() = ListSite.MAL
}

object KitsuTarget : AnimeTarget(
    "Kitsu",
    FeatureChannel::animeChannel,
    listOf(
        Regex("kitsu.io/users/[a-zA-Z0-9_]{3,20}")
    ),
    "kitsu", "kitsu.io"
) {
    override val dbSite: ListSite
        get() = ListSite.KITSU
}


data class TargetArguments(val site: TrackerTarget, val identifier: String) {

    companion object {
        // a bit rigid design-wise - enforces the current structure of the tracker classes to be nested.
        // however, simple and better than manually adding targets
        val declaredTargets = TrackerTarget::class.sealedSubclasses
            .flatMap { c -> c.sealedSubclasses }
            .mapNotNull { c -> c.objectInstance }

        fun parseFor(origin: DiscordParameters, inputArgs: List<String>, type: KClass<out TrackerTarget> = TrackerTarget::class): Result<TargetArguments, String> {
            // parse if the user provides a valid and enabled track target, either in the format of a matching URL or site name + account ID
            // empty 'args' is handled by initial command call erroring and should never occur here
            require(inputArgs.isNotEmpty()) { "Can not parse empty track target" }

            // get the channel features, if they exist. PMs do not require trackers to be enabled
            // thus, a URL or site name must be specified if used in PMs
            val features = if(origin.guild != null) {
                GuildConfigurations.getOrCreateGuild(origin.guild.id.asLong()).options.featureChannels[origin.guildChan.id.asLong()]
            } else null

            return if(inputArgs.size == 1) {

                // if 1 arg, user supplied just a username OR a url (containing site and username)
                val urlMatch = declaredTargets.map { supportedSite ->
                    supportedSite.url.mapNotNull { exactUrl ->
                        exactUrl.find(inputArgs[0])?.to(supportedSite)
                    }
                }.flatten().firstOrNull()

                if(urlMatch != null) {
                    Ok(
                        TargetArguments(
                            site = urlMatch.second,
                            identifier = urlMatch.first.groups[1]?.value!!
                        )
                    )
                } else {

                    // arg was not a supported url, but there was only 1 arg supplied. check if we are able to assume the track target for this channel
                    // simple ;track <username> is not supported for PMs
                    if(features == null) {
                        return Err("You must specify the site name for tracking in PMs.")
                    }

                    val default = features.findDefaultTarget(type)
                    if(default == null) {
                        Err("There are no website trackers enabled in **${origin.guildChan.name}**, so I can not determine the website you are trying to target. Please specify the site name.")
                    } else {
                        Ok(
                            TargetArguments(
                                site = default,
                                identifier = inputArgs[0]
                            )
                        )
                    }
                }
            } else {
                // 2 or more inputArgs - must be site and account id or invalid
                val siteArg = inputArgs[0].toLowerCase()
                val site = declaredTargets.firstOrNull { supportedSite ->
                    supportedSite.alias.contains(siteArg)
                }

                if(site == null) {
                    return Err("Unknown/unsupported target **${inputArgs[0]}**.")
                }

                Ok(TargetArguments(site = site, identifier = inputArgs[1]))
            }
        }
    }
}

