package moe.kabii.data.mongodb.guilds

import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.*
import moe.kabii.util.EmojiCharacters
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

// channel-specific features/settings
data class OptionalFeatures(
    val featureChannels: MutableMap<Long, FeatureChannel> = mutableMapOf(),
    var linkedTwitchChannel: TwitchConfig? = null)

data class FeatureChannel(
    val channelID: Long,
    var locked: Boolean = true,
    var twitchChannel: Boolean = false,
    var youtubeChannel: Boolean = false,
    var twitterChannel: Boolean = false,
    var animeChannel: Boolean = false,
    var logChannel: Boolean = false,
    var musicChannel: Boolean = false,
    var tempChannelCreation: Boolean = false,
    var allowStarboarding: Boolean = true,
    var cleanReactionRoles: Boolean = false,
    var defaultTracker: TrackerTarget? = null,
    val logSettings: LogSettings = LogSettings(channelID),
    val streamSettings: StreamSettings = StreamSettings(),
    val youtubeSettings: YoutubeSettings = YoutubeSettings(),
    val twitterSettings: TwitterSettings = TwitterSettings(),
    val animeSettings: AnimeSettings = AnimeSettings(),
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, youtubeChannel, twitterChannel, animeChannel, logChannel).any(true::equals)

    fun isStreamChannel() = booleanArrayOf(twitchChannel, youtubeChannel).any(true::equals)

    fun findDefaultTarget(type: KClass<out TrackerTarget> = TrackerTarget::class): TrackerTarget? {
        // if type is specified, this is a restriction on what type of target we need
        if(defaultTracker != null && type.isSuperclassOf(defaultTracker!!::class)) return defaultTracker

        // fallback to enabled tracker of specified type
        // if multiple are enabled, it will default in this order
        return when {
            twitchChannel && type.isSuperclassOf(TwitchTarget::class) -> TwitchTarget
            twitterChannel && type.isSuperclassOf(TwitterTarget::class) -> TwitterTarget
            youtubeChannel && type.isSuperclassOf(YoutubeTarget::class) -> YoutubeTarget
            animeChannel && type.isSuperclassOf(AnimeTarget::class) -> MALTarget
            else -> null
        }
    }

    fun validateDefaultTarget() {
        // if a default tracker is set, remove it if that feature is disabled
        if(defaultTracker == null) return
        
        val enabled = defaultTracker?.channelFeature?.get(this) ?: return
        if(!enabled) {
            defaultTracker = null
        }
    }
}

data class StreamSettings(
    var summaries: Boolean = true,
    var thumbnails: Boolean = true,
    var viewers: Boolean = true,
    var endGame: Boolean = true,

    var renameEnabled: Boolean = false,
    var notLive: String = "no-streams-live",
    var livePrefix: String = "${EmojiCharacters.liveCircle}-live-",
    var liveSuffix: String = "",
    val marks: MutableList<ChannelMark> = mutableListOf()
)

data class YoutubeSettings(
    var liveStreams: Boolean = true,
    var uploads: Boolean = true,
    var premieres: Boolean = true,
    var upcomingSummaryDuration: String? = null,
    var upcomingNotificationDuration: String? = null,
    var streamCreation: Boolean = false,

    var upcomingChannel: Long? = null
)

data class TwitterSettings(
    var displayNormalTweet: Boolean = true,
    var displayReplies: Boolean = false,
    var displayQuote: Boolean = true,
    var displayRetweet: Boolean = false
)

data class ChannelMark(
    val channel: MongoStreamChannel,
    val mark: String
)

// unfortunately, our other db objects are not directly serializable
data class MongoStreamChannel(
    val site: TrackedStreams.DBSite,
    val identifier: String
) {
    companion object {
        fun of(stream: BasicStreamChannel) = MongoStreamChannel(stream.site.dbSite, stream.accountId)
        fun of(stream: TrackedStreams.StreamChannel) = MongoStreamChannel(stream.site, stream.siteChannelID)
    }
}

data class AnimeSettings(
    var postNewItem: Boolean = true,
    var postStatusChange: Boolean = true,
    var postUpdatedStatus: Boolean = true
)