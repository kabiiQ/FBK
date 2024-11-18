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
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.posts.bluesky.BlueskyParser
import moe.kabii.trackers.posts.twitter.NitterParser
import moe.kabii.trackers.videos.kick.api.KickParser
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
            1L -> BlueskyTarget
            100L -> YoutubeTarget
            101L -> TwitchTarget
//            102L -> TwitterSpaceTarget
            103L -> TwitcastingTarget
            104L -> KickTarget
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

// enforces the properties required throughout the code to add a streaming site and relates them to each other
sealed class StreamingTarget(
    val serviceColor: Color,
    val available: Boolean,
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
    abstract suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr>
}

object TwitchTarget : StreamingTarget(
    TwitchParser.color,
    AvailableServices.twitchApi,
    "Twitch",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("twitch.tv/([a-zA-Z0-9_]{4,25})")
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
        TwitchParser.EventSub.createSubscription(TwitchEventSubscriptions.Type.START_STREAM, channel.siteChannelID.toLong())
    }
}

object YoutubeTarget : StreamingTarget(
    YoutubeParser.color,
    AvailableServices.youtube,
    "YouTube",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex(YoutubeParser.youtubeChannelPattern.pattern),
        Regex("youtube.com/channel/${YoutubeParser.youtubeChannelPattern.pattern}"),
        Regex("youtube.com/${YoutubeParser.youtubeHandlePattern.pattern}"),
        Regex("youtube.com/${YoutubeParser.youtubeNamePattern.pattern}")
    ),
    "youtube", "yt", "youtube.com", "utube", "ytube"
) {
    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.YOUTUBE

    override suspend fun getChannel(id: String): Result<BasicStreamChannel, TrackerErr> {
        return try {
            val channel = YoutubeParser.getChannelFromUnknown(id)
            if(channel != null) {
                val info = BasicStreamChannel(YoutubeTarget, channel.id, channel.name, channel.url)
                Ok(info)
            } else Err(TrackerErr.NotFound)
        } catch(e: Exception) {
            LOG.debug("Error getting YouTube channel: ${e.message}")
            LOG.trace(e.stackTraceString)
            Err(TrackerErr.IO)
        }
    }

    override fun feedById(id: String): String = URLUtil.StreamingSites.Youtube.channel(id)

    override val onTrack: TrackCallback = { _, channel ->
        YoutubeVideoIntake.intakeExisting(channel.siteChannelID)
    }
}

object YoutubeVideoTarget : TrackerTarget(
    "YouTubeVideos",
    FeatureChannel::streamTargetChannel,
    "ytvideo",
    listOf(
        YoutubeParser.youtubeVideoUrlPattern
    ),
    "youtubevideos", "ytvid"
) {
    override fun feedById(id: String) = if(id.matches(YoutubeParser.youtubeVideoUrlPattern)) id else URLUtil.StreamingSites.Youtube.video(id)
}

object TwitcastingTarget : StreamingTarget(
    TwitcastingParser.color,
    AvailableServices.twitCasting,
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

object KickTarget : StreamingTarget(
    KickParser.color,
    available = false,
    "Kick.com",
    FeatureChannel::streamTargetChannel,
    listOf(
        Regex("kick.com/([a-zA-Z0-9_]{4,25})")
    ),
    "kick", "kick.com"
) {

    override val dbSite: TrackedStreams.DBSite
        get() = TrackedStreams.DBSite.KICK

    override suspend fun getChannel(id: String) = try {
        val channel = KickParser.getChannel(id)
        if(channel != null) {
            Ok(BasicStreamChannel(KickTarget, channel.slug, channel.user.username, channel.url))
        } else Err(TrackerErr.NotFound)
    } catch(e: Exception) {
        LOG.debug("Error getting Kick channel: ${e.message}")
        LOG.debug(e.stackTraceString)
        Err(TrackerErr.IO)
    }

    override fun feedById(id: String) = URLUtil.StreamingSites.Kick.channelByName(id)

    override val onTrack: TrackCallback = { _, _ -> }
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

sealed class SocialTarget(
    val available: Boolean,
    full: String,
    url: List<Regex>,
    vararg alias: String
) : TrackerTarget(full, FeatureChannel::postsTargetChannel, "posts", url, *alias) {

    abstract val dbSite: TrackedSocialFeeds.DBSite

    abstract suspend fun getProfile(id: String): Result<BasicSocialFeed, TrackerErr>

    /**
     * Given a confirmed real site ID (from getProfile), get or create an associated SocialFeed
     */
    @RequiresExposedContext
    abstract suspend fun dbFeed(id: String, createFeedInfo: BasicSocialFeed? = null): TrackedSocialFeeds.SocialFeed?
}

object BlueskyTarget : SocialTarget(
    AvailableServices.bluesky,
    "Bluesky",
    listOf(
        Regex("/profile/@?(${BlueskyParser.handlePattern})"), // profile/name.domain
        Regex("@(${BlueskyParser.handlePattern})"), // @name.domain
        Regex("(${BlueskyParser.didPattern})"), // did:plc:identifier
        Regex("@?(${BlueskyParser.primaryDomainPattern})") // name.bsky.social (without needing @, but specific to bsky.social)
    ),
    "bluesky", "bsky", "bsky.app", "bsky.social"
) {
    override val dbSite = TrackedSocialFeeds.DBSite.BLUESKY

    override fun feedById(id: String) = URLUtil.Bluesky.feedUsername(id)

    override suspend fun getProfile(id: String): Result<BasicSocialFeed, TrackerErr> {
        return try {
            val feed = BlueskyParser.getProfile(id)
            if(feed != null) {
                Ok(BasicSocialFeed(BlueskyTarget, feed.did, feed.handle, URLUtil.Bluesky.feedUsername(feed.handle)))
            } else Err(TrackerErr.NotFound)
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

object TwitterTarget : SocialTarget(
    AvailableServices.nitter,
"Twitter",
    listOf(
        Regex("(?:twitter|x).com/([a-zA-Z0-9_]{4,15})"),
        //Regex("@([a-zA-Z0-9_]{4,15})")
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
            return Err(TrackerErr.NotPermitted("General Twitter feed tracking has been disabled indefinitely. The method FBK has used until now to access feeds has finally been shut down by Twitter.\n\nAt this time, there is no known solution that will allow us to bring back the Twitter tracker. A [limited number of popular feeds](http://content.kabii.moe:8080/twitterfeeds) are currently enabled for tracking."))
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

            var assistedInput = input
//            if(site is TwitterSpaceTarget) {
//                assistedInput = assistedInput.removePrefix("@")
//            }

            // check if 'username' matches a precise url for tracking
            val urlMatch = declaredTargets.map { supportedSite ->
                supportedSite.url.mapNotNull { exactUrl ->
                    exactUrl.find(assistedInput)?.to(supportedSite)
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
                Ok(TargetArguments(site, assistedInput))

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

