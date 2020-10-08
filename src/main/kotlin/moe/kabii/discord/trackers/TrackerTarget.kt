package moe.kabii.discord.trackers

import discord4j.rest.util.Color
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.MediaSite
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.trackers.streams.twitch.TwitchParser
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

sealed class TrackerTarget(
    val full: String,
    val channelFeature: KProperty1<FeatureChannel, Boolean>,
    val url: Regex,
    vararg val alias: String
)

// streaming targets
data class BasicStreamChannel(val site: StreamingTarget, val accountId: String, val displayName: String)

sealed class StreamingTarget(
    val dbSite: TrackedStreams.DBSite,
    val serviceColor: Color,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: Regex,
    vararg alias: String
) : TrackerTarget(full, channelFeature, url, *alias) {

    // return basic info about the stream, primarily just if it exists + account ID needed for DB
    abstract fun getChannel(id: String): Result<BasicStreamChannel, StreamErr>
    abstract fun getChannelById(id: String): Result<BasicStreamChannel, StreamErr>
}

object TwitchTarget : StreamingTarget(
    TrackedStreams.DBSite.TWITCH,
    TwitchParser.color,
    "Twitch",
    FeatureChannel::twitchChannel,
    Regex("twitch.tv/([a-zA-Z0-9_]{4,25})"),
    "twitch", "twitch.tv", "ttv"
) {

    override fun getChannel(id: String) = TwitchParser.getUser(id).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName) }
    override fun getChannelById(id: String) = TwitchParser.getUser(id.toLong()).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName) }
}

// anime targets
sealed class AnimeTarget(
    val dbSite: MediaSite,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: Regex,
    vararg alias: String
) : TrackerTarget(full, channelFeature, url, *alias)

object MALTarget : AnimeTarget(
    MediaSite.MAL,
    "MyAnimeList",
    FeatureChannel::animeChannel,
    Regex("myanimelist.net/(animelist|mangalist|profile)/[a-zA-Z0-9_]{2,16}"),
    "mal", "myanimelist", "myanimelist.net", "animelist", "mangalist"
)
object KitsuTarget : AnimeTarget(
    MediaSite.KITSU,
    "Kitsu",
    FeatureChannel::animeChannel,
    Regex("kitsu.io/users/[a-zA-Z0-9_]{3,20}"),
    "kitsu", "kitsu.io"
)


data class TargetArguments(val site: TrackerTarget, val identifier: String) {

    companion object {
        val declaredTargets = TrackerTarget::class.sealedSubclasses.mapNotNull { c -> c.objectInstance }

        fun parseFor(origin: DiscordParameters, inputArgs: List<String>, type: KClass<TrackerTarget> = TrackerTarget::class): Result<TargetArguments, String> {
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
                val urlMatch = declaredTargets.mapNotNull { supportedSite ->
                    supportedSite.url.find(inputArgs[0])?.to(supportedSite)
                }.firstOrNull()

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

