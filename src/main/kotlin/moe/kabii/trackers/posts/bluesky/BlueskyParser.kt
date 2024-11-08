package moe.kabii.trackers.posts.bluesky

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.posts.bluesky.json.BlueskyAuthor
import moe.kabii.trackers.posts.bluesky.json.BlueskyErrorResponse
import moe.kabii.trackers.posts.bluesky.json.BlueskyFeedResponse
import java.io.IOException

object BlueskyParser {

    private val errorAdapter = MOSHI.adapter(BlueskyErrorResponse::class.java)

    private inline fun <reified R: Any> publicRequest(call: String): R? {
        val request = newRequestBuilder()
            .get()
            .url(BlueskyRoutes.public(call))
            .build()

        val response = OkHTTP.newCall(request).execute()

        try {
            val body = response.body.string()
            if(response.isSuccessful) {
                return MOSHI.adapter(R::class.java).fromJson(body)!!
            } else {
                val error = errorAdapter.fromJson(body)

                LOG.warn("Bluesky API call returned an error :: $error")
                LOG.debug("Bluesky error response body: $body")
                throw IOException("Bluesky API call ${response.code}")
            }
        } finally {
            response.close()
        }
    }

    fun getFeed(identifier: String): BlueskyFeedResponse? = publicRequest("app.bsky.feed.getAuthorFeed?actor=$identifier")

    fun getProfile(identifier: String): BlueskyAuthor? = publicRequest("app.bsky.actor.getProfile?actor=$identifier")
}