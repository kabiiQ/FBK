package moe.kabii.trackers

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.flat.AvailableServices
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.data.relational.posts.TrackedSocialFeeds
import moe.kabii.data.relational.posts.bluesky.BlueskyFeed
import moe.kabii.data.relational.posts.twitter.NitterFeed
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyParser
import moe.kabii.trackers.posts.twitter.NitterParser
import moe.kabii.trackers.videos.kick.parser.KickNonPublic
import moe.kabii.trackers.videos.kick.parser.KickParser
import moe.kabii.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeVideoIntake
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.RequiresExposedContext
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.joda.time.DateTime
import org.joda.time.Duration
import java.net.URLDecoder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

sealed class TrackerTarget(
    val full: String,
    val channelFeature: KProperty1<FeatureChannel, Boolean>,
    val featureName: String,
    val url: List<URLMatch>,
    vararg val alias: String
) {
    open val mentionable: Boolean = false

    abstract fun feedById(id: String): String

    companion object {
        fun parseSiteArg(id: Long) = when(id) {
            0L -> TwitterTarget
            1L -> BlueskyTarget
            100L -> YoutubeTarget
            101L -> TwitchTarget
//            102L -> TwitterSpaceTarget
            103L -> TwitcastingTarget
            104L -> KickTarget
            198L -> HoloChatsTarget
            199L -> YoutubeVideoTarget
            200L -> MALTarget
            201L -> KitsuTarget
            202L -> AniListTarget
            else -> error("unmapped 'site' target: $id")
        }
    }
}

// streaming targets
data class BasicStreamChannel(val site: StreamingTarget, val accountId: String, val displayName: String, val url: String)

// media posts targets
data class BasicSocialFeed(val site: SocialTarget, val accountId: String, val displayName: String, val url: String)

typealias TrackCallback = (suspend (DiscordParameters, TrackedStreams.StreamChannel) -> Unit)?

// Enable prioritization of specific site URLs
data class URLMatch(val regex: Regex, val matchPriority: Int)

/**
 * Assign a site URL a priority value - lower values are used first (thus given priority)
 * In general, full URLs should probably be priority 1, definitive IDs 2 and problematic matches 3+
 */
infix fun Regex.priority(priority: Int) = URLMatch(this, priority)

// enforces the properties required throughout the code to add a streaming site and relates them to each other
sealed class StreamingTarget(
    val serviceColor: Color,
    val available: Boolean,
    full: String,
    channelFeature: KProperty1<FeatureChannel, Boolean>,
    url: List<URLMatch>,
    vararg alias: String
) : TrackerTarget(full, channelFeature, "streams", url, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: TrackedStreams.DBSite

    abstract val onTrack: TrackCallback

    override val mentionable = true

    // return basic info about the stream, primarily just if it exists + account ID needed for DB
    abstract suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr>
}

data object TwitchTarget : StreamingTarget(
    TwitchParser.color,
    AvailableServices.twitchApi,
    "Twitch",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})") priority 1
    ),
    "twitch", "twitch.tv", "ttv"
) {
    override val dbSite
        get() = TrackedStreams.DBSite.TWITCH

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr> {
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
    }
}

data object YoutubeTarget : StreamingTarget(
    YoutubeParser.color,
    AvailableServices.youtube,
    "YouTube",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("youtube.com/channel/${YoutubeParser.youtubeChannelPattern.pattern}") priority 1, // full channel url with 24-digit ID
        Regex("youtube.com/${YoutubeParser.youtubeHandlePattern.pattern}") priority 1, // newer youtube handle urls
        Regex("youtube.com/${YoutubeParser.youtubeNamePattern.pattern}") priority 1, // old youtube username urls
        Regex(YoutubeParser.youtubeChannelPattern.pattern) priority 2 // 24-digit ID
    ),
    "youtube", "yt", "youtube.com", "utube", "ytube"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.YOUTUBE

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr> {
        try {
            // Allow direct grab from database (no API call) if using exact channel ID
            if(id.matches(YoutubeParser.youtubeChannelPattern)) {
                val knownChannel = propagateTransaction {
                    TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.YOUTUBE, id)
                }
                if(knownChannel != null) {
                    val info = BasicStreamChannel(YoutubeTarget, knownChannel.siteChannelID, knownChannel.lastKnownUsername ?: "", URLUtil.StreamingSites.Youtube.channel(id))
                    return Ok(info)
                }
            }

            val channel = YoutubeParser.getChannelFromUnknown(id)
            return if(channel != null) {
                val info = BasicStreamChannel(YoutubeTarget, channel.id, channel.name, channel.url)
                Ok(info)
            } else Err(TrackerErr.NotFound)
        } catch(e: Exception) {
            LOG.debug("Error getting YouTube channel: ${e.message}")
            LOG.trace(e.stackTraceString)
            return Err(TrackerErr.IO)
        }
    }

    override fun feedById(id: String): String = URLUtil.StreamingSites.Youtube.channel(id)

    override val onTrack: TrackCallback = { _, channel ->
        YoutubeVideoIntake.intakeAsync(channel.siteChannelID)
    }
}

data object TwitcastingTarget : StreamingTarget(
    TwitcastingParser.color,
    AvailableServices.twitCasting,
    "TwitCasting",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("twitcasting.tv/(c:[a-zA-Z0-9_]{4,15})") priority 1,
        Regex("twitcasting.tv/([a-z0-9_]{4,18})") priority 1,
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
        } else Err(TrackerErr.NotFound)
    } catch(e: Exception) {
        LOG.debug("Error getting TwitCasting channel: ${e.message}")
        LOG.debug(e.stackTraceString)
        Err(TrackerErr.IO)
    }

    override val onTrack: TrackCallback = { origin, channel ->
        origin.handler.instances.services.twitCastChecker.checkUserForMovie(channel)
        TwitcastingParser.registerWebhook(channel.siteChannelID)
    }
}

data object KickTarget : StreamingTarget(
    KickParser.color,
    AvailableServices.kickApi,
    "Kick.com",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("kick.com/([a-zA-Z0-9_]{4,25})") priority 1
    ),
    "kick", "kick.com"
) {

    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.KICK

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr> {
        try {
            if(id.toLongOrNull() != null) {
                // If numerical ID, can only do a database match for now
                val knownChannel = propagateTransaction {
                    TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.KICK, id)
                }
                return if(knownChannel != null) {
                    val info = BasicStreamChannel(KickTarget, knownChannel.siteChannelID, knownChannel.lastKnownUsername!!, URLUtil.StreamingSites.Kick.channelByName(knownChannel.lastKnownUsername!!))
                    Ok(info)
                } else Err(TrackerErr.NotFound)
            }

            val channel = KickNonPublic.channelRequest(id)
            return if(channel != null) {
                Ok(BasicStreamChannel(KickTarget, channel.id.toString(), channel.user.username, channel.url))
            } else Err(TrackerErr.NotFound)
        } catch(e: Exception) {
            LOG.debug("Error getting Kick channel: ${e.message}")
            LOG.debug(e.stackTraceString)
            return Err(TrackerErr.IO)
        }
    }

    override fun feedById(id: String) = URLUtil.StreamingSites.Kick.channelByName(id)

    override val onTrack: TrackCallback = callback@{ origin, channel ->
        val services = origin.handler.instances.services
        val kick = services.kick

        val targets = kick.getActiveTargets(channel) ?: return@callback
        val stream = KickParser.getChannel(channel.siteChannelID.toLong()).orNull()
        if(stream != null) {
            kick.updateChannel(channel, stream, targets)
        }
    }
}

data object HoloChatsTarget : TrackerTarget(
    "HoloChats",
    FeatureChannel::holoChatsTargetChannel,
    "holochats",
    listOf(),
    "holochats", "chatrelay", "holorelay"
) {
    override fun feedById(id: String) = if(id.matches(YoutubeParser.youtubeVideoPattern)) URLUtil.StreamingSites.Youtube.video(id) else URLUtil.StreamingSites.Youtube.channel(id)
}

data object YoutubeVideoTarget : TrackerTarget(
    "YouTubeVideos",
    FeatureChannel::streamTargetChannel,
    "streams",
    listOf(
        YoutubeParser.youtubeVideoUrlPattern priority 1
    ),
    "youtubevideos", "ytvid"
) {
    override fun feedById(id: String) = if(id.matches(YoutubeParser.youtubeVideoPattern)) id else URLUtil.StreamingSites.Youtube.video(id)
}

// anime targets
sealed class AnimeTarget(
    full: String,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, FeatureChannel::animeTargetChannel, "anime", url.map { u -> u priority 1 }, *alias) {

    // dbsite should not be constructor property as these refer to each other - will not be initalized yet
    abstract val dbSite: ListSite

    override fun feedById(id: String): String = URLUtil.MediaListSite.url(dbSite, id)
}

data object MALTarget : AnimeTarget(
    "MyAnimeList",
    listOf(
        Regex("myanimelist\\.net/(?:animelist|mangalist|profile)/([a-zA-Z0-9_]{2,16})")
    ),
    "mal", "myanimelist", "myanimelist.net", "animelist", "mangalist"
) {
    override val dbSite: ListSite
        get() = ListSite.MAL
}

data object KitsuTarget : AnimeTarget(
    "Kitsu",
    listOf(
        Regex("kitsu\\.io/users/([a-zA-Z0-9_]{3,20})")
    ),
    "kitsu", "kitsu.io", "kitsu.app"
) {
    override val dbSite: ListSite
        get() = ListSite.KITSU
}

data object AniListTarget : AnimeTarget(
    "AniList",
    listOf(
        Regex("anilist\\.co/user/([a-zA-Z0-9]{2,20})")
    ),
    "anilist", "anlist", "anilist.co", "a.co"
) {
    override val dbSite: ListSite
        get() = ListSite.ANILIST
}

sealed class SocialTarget(
    val available: Boolean,
    full: String,
    url: List<URLMatch>,
    vararg alias: String
) : TrackerTarget(full, FeatureChannel::postsTargetChannel, "posts", url, *alias) {

    abstract val dbSite: TrackedSocialFeeds.DBSite

    abstract suspend fun getProfile(id: String): Result<BasicSocialFeed, TrackerErr>

    override val mentionable = true

    /**
     * Given a confirmed real site ID (from getProfile), get or create an associated SocialFeed
     */
    @RequiresExposedContext
    abstract suspend fun dbFeed(id: String, createFeedInfo: BasicSocialFeed? = null): TrackedSocialFeeds.SocialFeed?
}

data object BlueskyTarget : SocialTarget(
    AvailableServices.bluesky,
    "Bluesky",
    listOf(
        Regex("(${BlueskyParser.didPattern})") priority 1, // did:plc:identifier
        Regex("@?(${BlueskyParser.primaryDomainPattern})") priority 1, // name.bsky.social (without needing @, but specific to bsky.social)
        Regex("/profile/@?(${BlueskyParser.handlePattern})") priority 2, // profile/name.domain
        Regex("@(${BlueskyParser.handlePattern})") priority 2 // @name.domain
    ),
    "bluesky", "bsky", "bsky.app", "bsky.social"
) {
    override val dbSite = TrackedSocialFeeds.DBSite.BLUESKY

    override fun feedById(id: String) = URLUtil.Bluesky.feedUsername(id)

    override suspend fun getProfile(id: String): Result<BasicSocialFeed, TrackerErr> {
        return try {
            val feed = BlueskyParser.getProfile(id)
            feed.mapOk { profile ->
                BasicSocialFeed(BlueskyTarget, profile.did, profile.handle, URLUtil.Bluesky.feedUsername(profile.handle))
            }
        } catch(e: Exception) {
            LOG.info("Error getting Bluesky profile: ${e.message}")
            LOG.debug(e.stackTraceString)
            Err(TrackerErr.IO)
        }
    }

    override suspend fun dbFeed(id: String, createFeedInfo: BasicSocialFeed?): TrackedSocialFeeds.SocialFeed? {
        val existing = BlueskyFeed.findExisting(id)
        return when {
            existing != null -> existing.feed
            createFeedInfo != null -> {
                val baseFeed = TrackedSocialFeeds.SocialFeed.new {
                    this.site = TrackedSocialFeeds.DBSite.BLUESKY
                }

                BlueskyFeed.new {
                    this.feed = baseFeed
                    this.did = createFeedInfo.accountId
                    this.handle = createFeedInfo.displayName
                    this.displayName = createFeedInfo.displayName
                    this.lastPulledTime = DateTime.now() - Duration.standardHours(2)
                }

                baseFeed
            }
            else -> null
        }
    }
}

data object TwitterTarget : SocialTarget(
    AvailableServices.nitter,
"Twitter",
    listOf(
        Regex("(?:twitter|x).com/([a-zA-Z0-9_]{4,15})") priority 1,
        Regex("@([a-zA-Z0-9_]{4,15})") priority 3
    ),
    "twitter", "tweets", "twit", "twitr", "tr", "x", "x.com"
) {
    override val dbSite
        get() = TrackedSocialFeeds.DBSite.X

    override fun feedById(id: String) = URLUtil.Twitter.feed(id)

    override suspend fun getProfile(id: String): Result<BasicSocialFeed, TrackerErr> {
        // Don't perform network call for Twitter unless needed
        val name = id.removePrefix("@")
        if(!name.matches(NitterParser.twitterUsernameRegex)) {
            return Err(TrackerErr.NotFound)
        }

        val knownUser = propagateTransaction {
            NitterFeed.findExisting(name)
        }

        if(AvailableServices.twitterWhitelist && (knownUser == null || !knownUser.enabled)) {
            return Err(TrackerErr.NotPermitted("General Twitter feed tracking has been disabled indefinitely, as Twitter has made it increasingly difficult to access the site.\nA [limited number of popular feeds](${NettyFileServer.twitterFeeds}) are currently enabled for tracking.\n\nFBK now supports Bluesky feeds if that alternative is helpful for you."))
        }

        return if(knownUser == null) {
            // user not known, attempt to look up feed for user
            try {
                val twitter = NitterParser.getFeed(name) ?: return Err(TrackerErr.NotFound)
                val username = twitter.user.username
                Ok(BasicSocialFeed(TwitterTarget, username, username, URLUtil.Twitter.feedUsername(username)))
            } catch(e: Exception) {
                LOG.warn("Error getting Twitter feed: ${e.message}")
                LOG.debug(e.stackTraceString)
                Err(TrackerErr.IO)
            }
        } else {
            Ok(BasicSocialFeed(TwitterTarget, knownUser.username, knownUser.username, URLUtil.Twitter.feedUsername(knownUser.username)))
        }
    }

    override suspend fun dbFeed(id: String, createFeedInfo: BasicSocialFeed?): TrackedSocialFeeds.SocialFeed? {
        val existing = NitterFeed.findExisting(id)
        return when {
            existing != null -> existing.feed
            createFeedInfo != null -> {
                val baseFeed = TrackedSocialFeeds.SocialFeed.new {
                    this.site = TrackedSocialFeeds.DBSite.X
                }

                NitterFeed.new {
                    this.feed = baseFeed
                    this.username = createFeedInfo.accountId
                    this.enabled = false
                }

                baseFeed
            }
            else -> null
        }
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

            val colonArgs = input.split(":", limit = 2)
            if(colonArgs.size == 2 && !colonArgs[1].startsWith("/")) {
                // siteName:username autocomplete variant
                val match = TargetArguments[colonArgs[0]]
                return if(match != null) Ok(TargetArguments(match, colonArgs[1])) else Err("Invalid site: ${colonArgs[0]}. You can use the 'site' option in the command to select a site.")
            }

            val assistedInput = URLDecoder.decode(input, "UTF-8")
//            if(site is TwitterSpaceTarget) {
//                assistedInput = assistedInput.removePrefix("@")
//            }

            // check if 'username' matches a precise url for tracking
            data class Match(val result: MatchResult, val site: TrackerTarget, val priority: Int)
            val urlMatch = declaredTargets.map { supportedSite ->
                supportedSite.url.mapNotNull { url ->
                    url.regex.find(assistedInput)?.run {
                        Match(this, supportedSite, url.matchPriority)
                    }
                }
            }.flatten().minByOrNull(Match::priority)

            return when {
                site != null -> {
                    // if site was manually specified
                    Ok(TargetArguments(site, assistedInput))
                }
                urlMatch != null -> {
                    Ok(
                        TargetArguments(
                            site = urlMatch.site,
                            identifier = urlMatch.result.groups[1]?.value!!
                        )
                    )
                }
                else -> {
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
}

