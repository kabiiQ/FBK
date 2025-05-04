package moe.kabii.trackers.posts.bluesky.streaming

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyParser
import moe.kabii.trackers.posts.bluesky.xrpc.BlueskyRecheck
import moe.kabii.util.extensions.javaInstant
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

class JetstreamClient(val firehoseService: BlueskyFirehose) : WebSocketClient(endpoint()) {
    companion object {
        private val collections = listOf("app.bsky.feed.post", "app.bsky.feed.repost")
        private val eventParser = BlueskyParser.moshi.adapter(JetstreamEvent::class.java)
        private val handleScope = CoroutineScope(DiscordTaskPool.socialThreads + CoroutineName("Bluesky-Stream") + SupervisorJob())

        val baseEndpoint = "ws://jetstream:6008/subscribe"
        fun endpoint() = URI.create("$baseEndpoint?wantedCollections=${collections.joinToString("&wantedCollections=")}")
    }

    override fun onOpen(handshake: ServerHandshake) {
        LOG.info("Bluesky Jetstream connected")
    }

    override fun onMessage(bytes: ByteBuffer) {
    }

    override fun onMessage(data: String) {
        // Parse event stream and filter for new posts only
        try {
            val event = eventParser.fromJson(data)
            val commit = event?.commit
            // Filter event stream for new posts (type "commit" -> "create")
            if(
                commit == null // not a repo update event
                || commit.operation != JetstreamCommit.Operation.CREATE // not a new post event
                || !firehoseService.trackedFeeds.contains(event.did) // not a feed we have tracked
            ) return

            LOG.info("Streaming debug: post detected for ${event.did}")
            handleScope.launch {
                if(commit.collection == "app.bsky.feed.post" && event.commit.record != null && commit.record?.reply == null) {
                    // if this is a first-party post, we can pull it and handle directly
                    // this is only beneficial because an author feed call may be cached and miss sequential posts
                    LOG.info("${event.did}: post detected, pulling and posting")
                    val uri = "at://${event.did}/app.bsky.feed.post/${commit.rkey}"
                    val post = try {
                        BlueskyParser.getPosts(listOf(uri)).orNull()?.firstOrNull()
                    } catch(e: Exception) {
                        LOG.warn("Unable to pull Bluesky post reported from Firehose for ${event.did}: $uri :: ${e.message}")
                        LOG.debug(e.stackTraceString)
                        null
                    }
                    if(post != null) {
                        firehoseService.bluesky.handleFirstPartyPost(event.did, post)
                        return@launch
                    }
                }

                // reposts, replies will need more context from the user feed and can just expedite a normal feed update
                LOG.info("${event.did}: feed update required")
                firehoseService.bluesky.updateFeed(event.did)

                // Post may not be included in feed if request was cached (sequential posts)
                val updated = propagateTransaction {
                    val dbFeed = firehoseService.bluesky.getFeed(event.did)
                    val dbLastUpdate = dbFeed?.lastUpdated?.javaInstant ?: return@propagateTransaction true
                    dbLastUpdate >= commit.record?.createdAt
                }
                LOG.info("Feed up-to-date: $updated")
                if(!updated) {
                    BlueskyRecheck.scheduleUpdate(event.did, firehoseService.bluesky::updateFeed)
                }
            }

        } catch(e: Exception) {
            onError(e)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        LOG.info("Bluesky Jetstream disconnected: $code :: $reason")
    }

    override fun onError(error: Exception) {
        LOG.error("Bluesky Jetstream error: ", error)
    }
}