package moe.kabii.data.mongodb.guilds


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
    val logSettings: LogSettings = LogSettings(
        channelID
    ),
    val featureSettings: FeatureSettings = FeatureSettings()
) {
    fun anyEnabled() = booleanArrayOf(twitchChannel, animeChannel, logChannel).any(true::equals)
}

data class FeatureSettings(
    var streamSummaries: Boolean = true,
    var streamThumbnails: Boolean = true,
    var streamPeakViewers: Boolean = true,
    var streamAverageViewers: Boolean = true,
    var streamEndTitle: Boolean = true,
    var streamEndGame: Boolean = true,
    var mediaNewItem: Boolean = true,
    var mediaStatusChange: Boolean = true,
    var mediaUpdatedStatus: Boolean = true
)