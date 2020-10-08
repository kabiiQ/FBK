package moe.kabii.discord.trackers.streams

sealed class StreamErr {
    object NotFound : StreamErr()
    object IO : StreamErr()
}