package moe.kabii.trackers

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.trackers.videos.StreamErr
import moe.kabii.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
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
    open val mentionable: Boolean = false

    abstract fun feedById(id: String): String

    companion object {
        fun parseSiteArg(id: Long) = when(id) {
            0L -> TwitterTarget
            100L -> YoutubeTarget
            101L -> TwitchTarget
            102L -> TwitterSpaceTarget
            103L -> TwitcastingTarget
            200L -> MALTarget
            201L -> KitsuTarget
            202L -> AniListTarget
            else -> error("unmapped 'site' target: $id")
        }
    }
}

// streaming targets
data class BasicStreamChannel(val site: StreamingTarget, val accountId: String, val displayName: String, val url: String)

typealias TrackCallback = (suspend (DiscordParameters, TrackedStreams.StreamChannel) -> Unit)?

// enforces the properties required throughout the code to add a streaming site and relates them to each other
sealed class StreamingTarget(
    val serviceColor: Color,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, channelFeature, "streams", url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: TrackedStreams.DBSite

    abstract val onTrack: TrackCallback

    override val mentionable = true

    // return basic info about the stream, primarily just if it exists + account ID needed for DB
    abstract suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr>
}

object TwitchTarget : StreamingTarget(
    TwitchParser.color,
    "Twitch",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})")
    ),
    "twitch", "twitch.tv", "ttv"
) {
    override val dbSite
        get() = TrackedStreams.DBSite.TWITCH

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr> {
        val twitchId = id.toLongOrNull()
        val twitchUser = if(twitchId != null) TwitchParser.getUser(twitchId)
        else TwitchParser.getUser(id)
        return twitchUser.mapOk { ok ->
            BasicStreamChannel(TwitchTarget, ok.userID.toString(), ok.username, ok.url)
        }
    }

    override fun feedById(id: String): String {
            return TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.TWITCH, id)
                ?.lastKnownUsername
                ?.run(URLUtil.StreamingSites.Twitch::channelByName)
                ?: ""
    }

    override val onTrack: TrackCallback = callback@{ origin, channel ->
        val services = origin.handler.instances.services
        val twitch = services.twitch

        val targets = twitch.getActiveTargets(channel) ?: return@callback
        val stream = TwitchParser.getStream(channel.siteChannelID.toLong()).orNull()
        twitch.updateChannel(channel, stream, targets)
        TwitchParser.EventSub.createSubscription(TwitchEventSubscriptions.Type.START_STREAM, channel.siteChannelID.toLong())
    }
}

private const val youtubeRegex = "([a-zA-Z0-9-_]{24})"
object YoutubeTarget : StreamingTarget(
    YoutubeParser.color,
    "YouTube",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex(youtubeRegex),
        Regex("youtube.com/channel/$youtubeRegex")
    ),
    "youtube", "yt", "youtube.com", "utube", "ytube"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.YOUTUBE

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr> {
        return try {
            val channel = YoutubeParser.getChannelFromUnknown(id)
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

    override fun feedById(id: String): String = URLUtil.StreamingSites.Youtube.channel(id)

    override val onTrack: TrackCallback = { _, channel ->
        YoutubeVideoIntake.intakeExisting(channel.siteChannelID)
    }
}

object TwitcastingTarget : StreamingTarget(
    TwitcastingParser.color,
    "TwitCasting",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("twitcasting.tv/(c:[a-zA-Z0-9_]{4,15})"),
        Regex("twitcasting.tv/([a-z0-9_]{4,18})"),
    ),
    "twitcasting", "twitcast", "tcast"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.TWITCASTING

    override suspend fun getChannel(id: String) = getChannelByIdentifier(id)

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
        origin.handler.instances.services.twitcastChecker.checkUserForMovie(channel)
        TwitcastingParser.registerWebhook(channel.siteChannelID)
    }
}

object TwitterSpaceTarget : StreamingTarget(
    TwitterParser.color,
    "Twitter Spaces",
    FeatureChannel::streamTargetChannel,
    listOf(),
    "spaces", "twitterspace", "twitterspaces", "twitter_space", "twitter_spaces", "space"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.SPACES

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, StreamErr> {
        return try {
            val twitterId = id.toLongOrNull()
            val user = if(twitterId != null) TwitterParser.getUser(twitterId)
            else TwitterParser.getUser(id)
            if(user != null) Ok(BasicStreamChannel(TwitterSpaceTarget, user.id.toString(), user.username, user.url))
            else Err(StreamErr.NotFound)
        } catch(e: Exception) {
            LOG.debug("Error getting Twitter user (Spaces): ${e.message}")
            LOG.debug(e.stackTraceString)
            Err(StreamErr.IO)
        }
    }

    override val onTrack: TrackCallback = { origin, channel ->
        val twitterId = listOf(channel.siteChannelID)
        val space = TwitterParser.getSpacesByCreators(twitterId).firstOrNull()
        if(space != null) {
            origin.handler.instances.services.spaceChecker.updateSpace(channel, space)
        }
    }

    override fun feedById(id: String): String = URLUtil.Twitter.feed(id)
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

    override val mentionable = true
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

        operator fun get(name: String) = declaredTargets.find { supportedSite ->
            supportedSite.alias.contains(name)
        }

        private val suggestionStyle = Regex("(.+) \\(([A-Za-z]+)\\)")
        suspend fun parseFor(origin: DiscordParameters, input: String, site: TrackerTarget?): Result<TargetArguments, String> {
            // parse if the user provides a valid and enabled track target, either in the format of a matching URL or site name + account ID
            // get the channel features, if they exist. PMs do not require trackers to be enabled
            // thus, a URL or site name must be specified if used in PMs
            val features = if(origin.guild != null) {
                GuildConfigurations.getOrCreateGuild(origin.client.clientId, origin.guild.id.asLong()).getOrCreateFeatures(origin.guildChan.id.asLong())
            } else null

            val suggestion = suggestionStyle.find(input)
            if(suggestion != null) {
                val siteArg = suggestion.groups[2]!!.value.lowercase()
                val match = TargetArguments[siteArg]
                return if(match != null) Ok(TargetArguments(match, suggestion.groups[1]!!.value)) else Err("Invalid site: $siteArg. You may have accidentally edited a suggested option.")
            }

            val colonArgs = input.split(":")
            if(colonArgs.size == 2 && !colonArgs[1].startsWith("/")) {
                // siteName:username autocomplete variant
                val match = TargetArguments[colonArgs[0]]
                return if(match != null) Ok(TargetArguments(match, colonArgs[1])) else Err("Invalid site: ${colonArgs[0]}. You can use the 'site' option in the command to select a site.")
            }

            // check if 'username' matches a precise url for tracking
            val urlMatch = declaredTargets.map { supportedSite ->
                supportedSite.url.mapNotNull { exactUrl ->
                    exactUrl.find(input)?.to(supportedSite)
                }
            }.flatten().firstOrNull()

            return if(urlMatch != null) {
                Ok(
                    TargetArguments(
                        site = urlMatch.second,
                        identifier = urlMatch.first.groups[1]?.value!!
                    )
                )
            } else if(site != null) {

                // if site was manually specified
                Ok(TargetArguments(site, input))

            } else {
                // arg was not a supported url, but there was only 1 arg supplied. check if we are able to assume the track target for this channel
                // simple /track <username> is not supported for PMs
                if(features == null) {
                    return Err("You must specify the site name for tracking in PMs.")
                }

                val default = features.findDefaultTarget()
                if(default == null) {
                    Err("There are no website trackers enabled in **${origin.guildChan.name}**, so I can not determine the website you are trying to target. Please specify the site name.")
                } else {
                    Ok(
                        TargetArguments(
                            site = default,
                            identifier = input
                        )
                    )
                }
            }
        }
    }
}

