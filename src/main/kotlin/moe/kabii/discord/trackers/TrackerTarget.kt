package moe.kabii.discord.trackers

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.data.relational.ps2.PS2Tracks
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.videos.StreamErr
import moe.kabii.discord.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.discord.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.discord.trackers.videos.youtube.YoutubeParser
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.stackTraceString
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

sealed class TrackerTarget(
    val full: String,
    val channelFeature: KProperty1<FeatureChannel, Boolean>,
    val featureName: String,
    val url: List<Regex>,
    vararg val alias: String
) {
    abstract fun feedById(id: String): String
}

// streaming targets
data class BasicStreamChannel(val site: StreamingTarget, val accountId: String, val displayName: String, val url: String)

typealias TrackCallback = (suspend (DiscordParameters, TrackedStreams.StreamChannel) -> Unit)?

// enforces the properties required throughout the code to add a streaming site and relates them to each other
sealed class StreamingTarget(
    val serviceColor: Color,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    featureName: String,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, channelFeature, featureName, url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: TrackedStreams.DBSite

    abstract val onTrack: TrackCallback

    // return basic info about the stream, primarily just if it exists + account ID needed for DB
    abstract suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr>
    abstract suspend fun getChannelById(id: String): Result<BasicStreamChannel, StreamErr>
}

object TwitchTarget : StreamingTarget(
    TwitchParser.color,
    "Twitch",
    FeatureChannel::streamTargetChannel,
    "twitch",
    listOf(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})")
    ),
    "twitch", "twitch.tv", "ttv"
) {
    override val dbSite
        get() = TrackedStreams.DBSite.TWITCH

    override suspend fun getChannel(id: String) = TwitchParser.getUser(id).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName, ok.url) }
    override suspend fun getChannelById(id: String) = TwitchParser.getUser(id.toLong()).mapOk { ok -> BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.displayName, ok.url) }

    override fun feedById(id: String): String = "" // unavailable without making requests. todo store in db ?

    override val onTrack: TrackCallback = callback@{ origin, channel ->
        val services = origin.handler.services
        val twitch = services.twitch

        val targets = twitch.getActiveTargets(channel) ?: return@callback
        val stream = TwitchParser.getStream(channel.siteChannelID.toLong()).orNull()
        twitch.updateChannel(channel, stream, targets)
        services.twitchFeedSub.subscribe(channel.siteChannelID)
    }
}

private const val youtubeRegex = "([a-zA-Z0-9-_]{24})"
object YoutubeTarget : StreamingTarget(
    YoutubeParser.color,
    "YouTube",
    FeatureChannel::streamTargetChannel,
    "youtube",
    listOf(
        Regex(youtubeRegex),
        Regex("youtube.com/channel/$youtubeRegex")
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

    override fun feedById(id: String): String = URLUtil.StreamingSites.Youtube.channel(id)

    override val onTrack: TrackCallback = { _, channel ->
        YoutubeVideoIntake.intakeExisting(channel.siteChannelID)
    }
}

object TwitcastingTarget : StreamingTarget(
    TwitcastingParser.color,
    "TwitCasting",
    FeatureChannel::streamTargetChannel,
    "twitcasting",
    listOf(
        Regex("twitcasting.tv/(c:[a-zA-Z0-9_]{4,15})"),
        Regex("twitcasting.tv/([a-z0-9_]{4,18})"),
    ),
    "twitcasting", "twitcast", "tcast"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.TWITCASTING

    override suspend fun getChannel(id: String) = getChannelByIdentifier(id)
    override suspend fun getChannelById(id: String) = getChannelByIdentifier(id)

    private val twitcastingNameType = Regex("c:[a-zA-Z0-9_]{4,15}")
    override fun feedById(id: String)
        = if(id.matches(twitcastingNameType)) URLUtil.StreamingSites.TwitCasting.channelByName(id) else ""

    private suspend fun getChannelByIdentifier(identifier: String) = try {
        val user = TwitcastingParser.searchUser(identifier)
        if(user != null) {
            Ok(BasicStreamChannel(TwitcastingTarget, user.userId, user.screenId, user.url))
        } else Err(StreamErr.NotFound)
    } catch(e: Exception) {
        LOG.debug("Error getting TwitCasting channel: ${e.message}")
        LOG.debug(e.stackTraceString)
        Err(StreamErr.IO)
    }

    override val onTrack: TrackCallback = { origin, channel ->
        origin.handler.services.twitcastChecker.checkUserForMovie(channel)
        TwitcastingParser.registerWebhook(channel.siteChannelID)
    }
}

// anime targets
sealed class AnimeTarget(
    full: String,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, FeatureChannel::animeTargetChannel, "anime", url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: ListSite

    override fun feedById(id: String): String = URLUtil.MediaListSite.url(dbSite, id)
}

object MALTarget : AnimeTarget(
    "MyAnimeList",
    listOf(
        Regex("myanimelist\\.net/(?:animelist|mangalist|profile)/([a-zA-Z0-9_]{2,16})")
    ),
    "mal", "myanimelist", "myanimelist.net", "animelist", "mangalist"
) {
    override val dbSite: ListSite
        get() = ListSite.MAL
}

object KitsuTarget : AnimeTarget(
    "Kitsu",
    listOf(
        Regex("kitsu\\.io/users/([a-zA-Z0-9_]{3,20})")
    ),
    "kitsu", "kitsu.io"
) {
    override val dbSite: ListSite
        get() = ListSite.KITSU
}

object AniListTarget : AnimeTarget(
    "AniList",
    listOf(
        Regex("anilist\\.co/user/([a-zA-Z0-9]{2,20})")
    ),
    "anilist", "anlist", "anilist.co", "a.co"
) {
    override val dbSite: ListSite
        get() = ListSite.ANILIST
}

object TwitterTarget : TrackerTarget(
    "Twitter",
    FeatureChannel::twitterTargetChannel,
    "twitter",
    listOf(
        Regex("twitter.com/([a-zA-Z0-9_]{4,15})"),
        Regex("@([a-zA-Z0-9_]{4,15})")
    ),
    "twitter", "tweets", "twit", "twitr", "tr"
) {
    override fun feedById(id: String): String = URLUtil.Twitter.feed(id)
}

sealed class PS2Target(
    full: String,
    vararg alias: String
) : TrackerTarget(full, FeatureChannel::ps2Channel, "ps2", listOf(), *alias) {

    override fun feedById(id: String): String = ""
    abstract val dbType: PS2Tracks.PS2EventType

    sealed class Outfit(vararg alias: String) : PS2Target("Outfit member logins", *alias) {
        object OutfitById : Outfit()
        object OutfitByName : Outfit("ps2outfit:name")
        object OutfitByTag : Outfit("ps2outfit:tag", "ps2outfit", "psoutfit")

        override val dbType: PS2Tracks.PS2EventType
            get() = PS2Tracks.PS2EventType.OUTFIT
    }
    object Player : PS2Target("Player logins", "ps2player", "ps2players", "psplayer", "psplayers") {
        override val dbType: PS2Tracks.PS2EventType
            get() = PS2Tracks.PS2EventType.PLAYER

        override fun feedById(id: String): String = "https://www.planetside2.com/players/#!/$id"
    }
    object ContinentEvent : PS2Target("Continent events", "ps2continent", "ps2cont", "ps2continents", "pscontinent", "pscontinents", "pscont") {
        override val dbType: PS2Tracks.PS2EventType
            get() = PS2Tracks.PS2EventType.CONTINENT
    }
    object OutfitCaptures : PS2Target("Outfit base captures", "ps2caps", "ps2outfitcap", "ps2outfitbasecap", "ps2outfitbase", "ps2basecap", "pscap", "psoutfitcap", "psoutfitbasecap", "psoutfitbase", "psbasecap") {
        override val dbType: PS2Tracks.PS2EventType
            get() = PS2Tracks.PS2EventType.OUTFITCAP
    }
}

data class TargetArguments(val site: TrackerTarget, val identifier: String) {

    companion object {
        val declaredTargets: List<TrackerTarget>

        init {
            val targets = mutableListOf<TrackerTarget>()
            fun addSubClasses(root: KClass<out TrackerTarget>) {
                root.sealedSubclasses.mapNotNullTo(targets) { kclass ->
                    addSubClasses(kclass)
                    kclass.objectInstance
                }
            }
            addSubClasses(TrackerTarget::class)
            declaredTargets = targets
        }

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

