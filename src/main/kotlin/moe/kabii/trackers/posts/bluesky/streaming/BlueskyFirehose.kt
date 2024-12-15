package moe.kabii.trackers.posts.bluesky.streaming

import kotlinx.coroutines.time.delay
import moe.kabii.data.relational.posts.bluesky.BlueskyFeed
import moe.kabii.trackers.posts.bluesky.BlueskyChecker
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import java.time.Duration

class BlueskyFirehose(val bluesky: BlueskyChecker) : Runnable {

    lateinit var trackedFeeds: List<String>

    suspend fun updateCachedFeeds() {
        trackedFeeds = propagateTransaction {
            BlueskyFeed
                .all()
                .map(BlueskyFeed::did)
                .toList()
        }
    }

    override fun run() {
        var client: JetstreamClient? = null
        applicationLoop {
            // Maintain websocket connection to Bluesky Firehose
            if(client == null || !client!!.isOpen) {
                updateCachedFeeds()
                client = JetstreamClient(this)
                client!!.connectBlocking()
            }
            delay(Duration.ofSeconds(2))
        }
    }
}