package moe.kabii.data.mongodb.guilds

import moe.kabii.data.relational.TrackedStreams

data class GuildSettings(
    var embedMessages: Boolean = true,
    var followRoles: Boolean = true,
    var reassignRoles: Boolean = false,
    var defaultFollowChannel: TrackedStreams.StreamInfo? = null,
    var twitchURLInfo: Boolean = false,
    var utilizeInvites: Boolean = true
)

data class TwitchConfig(
        var twitchid: Long,
        var urlTitles: Boolean = true)

data class TempChannels(
    val tempChannels: MutableList<Long> = mutableListOf()
)