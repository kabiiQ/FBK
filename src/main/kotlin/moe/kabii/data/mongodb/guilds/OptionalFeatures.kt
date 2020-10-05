package moe.kabii.data.mongodb.guilds

import moe.kabii.command.commands.trackers.TargetMatch


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
    var defaultTracker: TargetMatch? = null,
    val logSettings: LogSettings = LogSettings(channelID),
    val twitchSettings: TwitchSettings = TwitchSettings(),
    val animeSettings: AnimeSettings = AnimeSettings(),
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, animeChannel, logChannel).any(true::equals)

    fun findDefaultTarget(): TargetMatch? {
        if(defaultTracker != null) return checkNotNull(defaultTracker)

        // fallback to enabled tracker
        return when {
            // twitch first, so if multiple are enabled, twitch will be the default
            twitchChannel -> TargetMatch.TWITCH
            animeChannel -> TargetMatch.MAL
            else -> null
        }
    }

    fun findDefaultTarget(vararg subset: TargetMatch): TargetMatch? {
        if(defaultTracker != null && subset.contains(defaultTracker)) return checkNotNull(defaultTracker)

        return when {
            twitchChannel && subset.contains(TargetMatch.TWITCH) -> TargetMatch.TWITCH
            animeChannel && subset.contains(TargetMatch.MAL) -> TargetMatch.MAL
            else -> null
        }
    }

    fun targetFeatureEnabled(target: TargetMatch): Boolean {
        // match tracker targets to associated channel features
        return when(target) {
            TargetMatch.TWITCH -> twitchChannel
            TargetMatch.MAL -> animeChannel
            TargetMatch.KITSU -> animeChannel
        }
    }

    fun validateDefaultTarget() {
        // if a default tracker is set, remove it if that feature is disabled
        val defaultEnabled = targetFeatureEnabled(defaultTracker ?: return)

        if(!defaultEnabled) {
            defaultTracker = null
        }
    }
}

data class TwitchSettings(
    var summaries: Boolean = true,
    var thumbnails: Boolean = true,
    var peakViewers: Boolean = true,
    var averageViewers: Boolean = true,
    var endTitle: Boolean = true,
    var endGame: Boolean = true
)

data class YoutubeSettings(
    var vodInfo: Boolean = true,
)

data class AnimeSettings(
    var postNewItem: Boolean = true,
    var postStatusChange: Boolean = true,
    var postUpdatedStatus: Boolean = true
)