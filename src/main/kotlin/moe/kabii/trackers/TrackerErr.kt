package moe.kabii.trackers

sealed class TrackerErr {
    data object NotFound : TrackerErr()

    open class Network : TrackerErr()
    data object IO : Network()
    data class NotPermitted(val reason: String) : Network()
}