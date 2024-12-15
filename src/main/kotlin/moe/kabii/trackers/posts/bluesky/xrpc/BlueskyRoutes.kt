package moe.kabii.trackers.posts.bluesky.xrpc

object BlueskyRoutes {
    val coreApi = "https://bsky.social/xrpc/"
    val publicApi = "https://public.api.bsky.app/xrpc/"

    fun api(call: String) = "$coreApi$call"

    fun public(call: String) = "$publicApi$call"
}