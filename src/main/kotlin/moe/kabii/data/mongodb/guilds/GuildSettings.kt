package moe.kabii.data.mongodb.guilds

import moe.kabii.data.relational.TrackedStreams

data class GuildSettings(
    var embedMessages: Boolean = true,
    var followRoles: Boolean = true,
    var reassignRoles: Boolean = false,
    var defaultFollow: StreamInfo? = null,
    var twitchURLInfo: Boolean = false,
    var utilizeInvites: Boolean = false
)

data class StreamInfo(
    val site: TrackedStreams.DBSite,
    val id: String
)

data class TwitchConfig(
        var twitchid: Long,
        var urlTitles: Boolean = true)

data class TempChannels(
    val tempChannels: MutableList<Long> = mutableListOf()
)