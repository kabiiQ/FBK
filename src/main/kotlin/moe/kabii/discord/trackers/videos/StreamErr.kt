package moe.kabii.discord.trackers.videos

sealed class StreamErr {
    object NotFound : StreamErr()
    object IO : StreamErr()
}