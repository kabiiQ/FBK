package moe.kabii.data.mongodb.guilds

import moe.kabii.discord.trackers.AnimeTarget
import moe.kabii.discord.trackers.MALTarget
import moe.kabii.discord.trackers.TrackerTarget
import moe.kabii.discord.trackers.TwitchTarget
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

// channel-specific features/settings
data class OptionalFeatures(
    val featureChannels: MutableMap<Long, FeatureChannel> = mutableMapOf(),
    var linkedTwitchChannel: TwitchConfig? = null)

data class FeatureChannel(
    val channelID: Long,
    var twitchChannel: Boolean = false,
    var animeChannel: Boolean = false,
    var logChannel: Boolean = false,
    var musicChannel: Boolean = false,
    var tempChannelCreation: Boolean = false,
    var allowStarboarding: Boolean = true,
    var defaultTracker: TrackerTarget? = null,
    val logSettings: LogSettings = LogSettings(channelID),
    val streamSettings: StreamSettings = StreamSettings(),
    val animeSettings: AnimeSettings = AnimeSettings(),
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, animeChannel, logChannel).any(true::equals)

    fun findDefaultTarget(type: KClass<TrackerTarget> = TrackerTarget::class): TrackerTarget? {
        // if type is specified, this is a restriction on what type of target we need
        if(defaultTracker != null && type.isSuperclassOf(defaultTracker!!::class)) return defaultTracker

        // fallback to enabled tracker of specified type
        // twitch first, so if multiple are enabled, twitch will be the default
        return when {
            twitchChannel && type.isSuperclassOf(TwitchTarget::class) -> TwitchTarget
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
    var peakViewers: Boolean = true,
    var averageViewers: Boolean = true,
    var endTitle: Boolean = true,
    var endGame: Boolean = true
)

data class AnimeSettings(
    var postNewItem: Boolean = true,
    var postStatusChange: Boolean = true,
    var postUpdatedStatus: Boolean = true
)