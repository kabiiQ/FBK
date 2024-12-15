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
        //val baseEndpoint = "wss://jetstream2.us-east.bsky.network/subscribe"
        //val endpoint = URI.create("wss://bsky.network/xrpc/com.atproto.sync.subscribeRepos")

        fun endpoint() = URI.create("$baseEndpoint?wantedCollections=${collections.joinToString("&wantedCollections=")}")
    }

    override fun onOpen(handshake: ServerHandshake) {
        LOG.info("Bluesky Jetstream connected")
    }

    override fun onMessage(bytes: ByteBuffer) {
        /*        try {

            *//* Bluesky Firehose implementation not well-documented at the time of this integration
            A fair bit of reverse-engineering was needed, I'm sure some details were missed or misconceptions made
             *//*


        }*/




        /*        val data = bytes!!.array()
                val stream = ByteArrayInputStream(data)
                val a = CborDecoder(stream)
                val ab = CborObject.deserialize(a, data.size) as CborMap
                //LOG.info(ab.values.entries.joinToString { erm  -> "${erm.key} : ${erm.value}" })
                val ac = CborObject.deserialize(a, data.size) as CborMap
                val ops = (ac.values[CborString("ops")] as CborList).value
                val opMaps = ops.map { it as CborMap }.map { it.values.entries.joinToString { erm -> "${erm.key} : ${erm.value}" } }
                val blobs = (ac.values.get(CborString("blobs")) as CborList).value
                val blocks = ac.values[CborString("blocks")] as CborByteArray

        *//*        val blockdata = blocks.value
        val blockstream = ByteArrayInputStream(blockdata)
        val c = CborDecoder(blockstream)*//*
*//*        while(blockstream.available() > 0) {
            val block = CborObject.deserialize(c, blockdata.size)
            LOG.info("block: ${block.toString() }")
        }*//*
        //val blockdata = CborObject.fromByteArray(blocks.value)
        val commit = (ac.values[CborString("commit")] as CborMerkleLink).target
        LOG.info(ac.values.entries.joinToString { erm  -> "${erm.key} : ${erm.value}" })
        LOG.info("ops: $opMaps")
        LOG.info("blobs: $blobs")
        LOG.info("blocks: $blockdata")
        LOG.info("commit: $commit")
        LOG.info("\n")*/
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