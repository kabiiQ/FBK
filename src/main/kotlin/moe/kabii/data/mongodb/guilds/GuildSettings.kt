package moe.kabii.data.mongodb.guilds

import moe.kabii.data.relational.streams.TrackedStreams

data class GuildSettings(
    var embedMessages: Boolean = true,
    var reassignRoles: Boolean = false,
    var defaultFollow: StreamInfo? = null,
    var utilizeInvites: Boolean = false,
    var utilizeAuditLogs: Boolean = true,
    var publishTrackerMessages: Boolean = false,
    var ps2Commands: Boolean = false
)

data class StreamInfo(
    val site: TrackedStreams.DBSite,
    val id: String
)

data class TempChannels(
    val tempChannels: MutableList<Long> = mutableListOf()
)