package moe.kabii.trackers.videos

sealed class StreamErr {
    object NotFound : StreamErr()
    object IO : StreamErr()
}