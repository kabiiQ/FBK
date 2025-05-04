package moe.kabii.data.mongodb.guilds

import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.*
import moe.kabii.util.constants.EmojiCharacters
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSuperclassOf

// channel-specific features/settings
data class OptionalFeatures(
    val featureChannels: MutableMap<Long, FeatureChannel> = mutableMapOf()
) {
    fun getChannels(feature: KProperty1<FeatureChannel, Boolean>)
        = featureChannels.filter { (_, chan) -> feature.get(chan) }
}

data class FeatureChannel(
    val channelID: Long,
    var locked: Boolean = true,

    // trackers
    var streamTargetChannel: Boolean = true,
    var postsTargetChannel: Boolean = true,
    var animeTargetChannel: Boolean = true,
    var holoChatsTargetChannel: Boolean = true,

    // other commands
    var musicChannel: Boolean = false,
    var logChannel: Boolean = false,
    var tempChannelCreation: Boolean = false,

    var allowStarboarding: Boolean = true,
    var cleanReactionRoles: Boolean = false,

    var trackerDefault: String? = null,
    val logSettings: LogSettings = LogSettings(channelID),
    val streamSettings: StreamSettings = StreamSettings(),
    val youtubeSettings: YoutubeSettings = YoutubeSettings(),
    val postsSettings: PostsSettings = PostsSettings(),
    val animeSettings: AnimeSettings = AnimeSettings(),
) {
    fun anyEnabled() = booleanArrayOf(musicChannel, tempChannelCreation, logChannel).any(true::equals)
    fun anyDefaultDisabled() = booleanArrayOf(streamTargetChannel, postsTargetChannel, animeTargetChannel).any(false::equals)

    fun defaultTracker() = trackerDefault?.run(TargetArguments::get)

    fun findDefaultTarget(type: KClass<out TrackerTarget> = TrackerTarget::class): TrackerTarget? {
        // if type is specified, this is a restriction on what type of target we need
        if(trackerDefault != null) {
            val default = defaultTracker()
            if(type.isSuperclassOf(default!!::class)) return default
        }

        // fallback to enabled tracker of specified type
        // if multiple are enabled, it will default in this order
        return when {
            streamTargetChannel && type.isSuperclassOf(StreamingTarget::class) -> TwitchTarget
            postsTargetChannel && type.isSuperclassOf(SocialTarget::class) -> TwitterTarget
            animeTargetChannel && type.isSuperclassOf(AnimeTarget::class) -> MALTarget
            else -> null
        }
    }

    fun validateDefaultTarget() {
        // if a default tracker is set, remove it if that feature is disabled
        if(trackerDefault == null) return

        val enabled = defaultTracker()?.channelFeature?.get(this) ?: return
        if(!enabled) {
            trackerDefault = null
        }
    }
}

data class StreamSettings(
    var summaries: Boolean = true,
    var thumbnails: Boolean = true,
    var viewers: Boolean = true,
    var endGame: Boolean = true,
    var mentionRoles: Boolean = true,

    var includeUrl: Boolean = false,
    var renameEnabled: Boolean = false,
    var pinActive: Boolean = false,
    var discordEvents: Boolean = false,

    var notLive: String = "no-streams-live",
    var livePrefix: String = "${EmojiCharacters.liveCircle}-live-",
    var liveSuffix: String = "",
    val marks: MutableList<ChannelMark> = mutableListOf()
)

data class YoutubeSettings(
    var liveStreams: Boolean = true,
    var uploads: Boolean = true,
    var premieres: Boolean = true,
    var upcomingNotificationDuration: String? = null,
    var streamCreation: Boolean = false,
    var includeMemberContent: Boolean = true,
    var includePublicContent: Boolean = true,
    var includeShortUploads: Boolean = true,
    var includeNormalUploads: Boolean = true,

    var upcomingChannel: Long? = null
)

data class PostsSettings(
    var displayNormalPosts: Boolean = true,
    var displayReplies: Boolean = false,
    var displayQuote: Boolean = true,
    var displayReposts: Boolean = false,
    var mediaOnly: Boolean = false,
    var autoTranslate: Boolean = false,
    var useComponents: Boolean = false,

    var mentionRoles: Boolean = true,
    var mentionNormalPosts: Boolean = true,
    var mentionReplies: Boolean = true,
    var mentionQuotes: Boolean = true,
    var mentionReposts: Boolean = false,

    var customTwitterDomain: String? = null
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