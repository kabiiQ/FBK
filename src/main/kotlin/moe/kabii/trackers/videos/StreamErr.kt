package moe.kabii.trackers.videos

sealed class StreamErr {
    data object NotFound : StreamErr()

    open class Network : StreamErr()
    data object IO : Network()
}