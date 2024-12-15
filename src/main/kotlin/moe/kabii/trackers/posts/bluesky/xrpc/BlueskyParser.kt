package moe.kabii.trackers.posts.bluesky.xrpc

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import moe.kabii.LOG
import moe.kabii.OkHTTP
import moe.kabii.newRequestBuilder
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.posts.bluesky.xrpc.json.*
import moe.kabii.util.extensions.stackTraceString

object BlueskyParser {
    val moshi: Moshi = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(BlueskyPostViewBase::class.java, "\$type")
                .withSubtype(BlueskyPostView::class.java, "app.bsky.feed.defs#postView")
                .withSubtype(BlueskyNotFoundPost::class.java, "app.bsky.feed.defs#notFoundPost")
                .withDefaultValue(null)
        )
        .add(
            PolymorphicJsonAdapterFactory.of(BlueskyEmbeddedBase::class.java, "\$type")
                .withSubtype(BlueskyEmbedImagesView::class.java, "app.bsky.embed.images#view")
                .withSubtype(BlueskyEmbedVideoView::class.java, "app.bsky.embed.video#view")
                .withSubtype(BlueskyEmbedExternalView::class.java, "app.bsky.embed.external#view")
                .withSubtype(BlueskyEmbedRecordView::class.java, "app.bsky.embed.record#view")
                .withSubtype(BlueskyEmbedRecordWithMediaView::class.java, "app.bsky.embed.recordWithMedia#view")
                .withDefaultValue(null)
        )
        .add(
            PolymorphicJsonAdapterFactory.of(BlueskyEmbeddedRecordBase::class.java, "\$type")
                .withSubtype(BlueskyEmbedViewRecord::class.java, "app.bsky.embed.record#viewRecord")
                .withSubtype(BlueskyEmbedNotFound::class.java, "app.bsky.embed.record#viewNotFound")
                .withDefaultValue(null)
        )
        .add(KotlinJsonAdapterFactory())
        .build()

    val handlePattern = Regex("(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]")
    val primaryDomainPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9-]{0,61}\\.bsky\\.social")
    val didPattern = Regex("did:plc:[a-z2-7]{24}")

    private val errorAdapter = moshi.adapter(BlueskyErrorResponse::class.java)

    private inline fun <reified R: Any> publicRequest(call: String): Result<R, TrackerErr> {
        val request = newRequestBuilder()
            .get()
            .url(BlueskyRoutes.public(call))
            .build()

        val response = OkHTTP.newCall(request).execute()

        try {
            val body = response.body.string()
            if(response.isSuccessful) {
                return Ok(moshi.adapter(R::class.java).fromJson(body)!!)
            } else {
                LOG.debug("Bluesky error response body: $body")
                val error = try {
                    errorAdapter.fromJson(body)
                } catch(e: Exception) {
                    LOG.warn("Bluesky JSON error: ${e.message}")
                    LOG.debug(e.stackTraceString)
                    null
                }

                LOG.warn("Bluesky API call returned an error: ${response.code} :: $error")
                return if(error?.message == "Profile not found") Err(TrackerErr.NotFound) else Err(TrackerErr.IO)
            }
        } finally {
            response.close()
        }
    }

    fun getFeed(identifier: String): Result<BlueskyFeedResponse, TrackerErr> = publicRequest("app.bsky.feed.getAuthorFeed?actor=$identifier")

    fun getProfile(identifier: String): Result<BlueskyAuthor, TrackerErr> = publicRequest("app.bsky.actor.getProfile?actor=$identifier")

    fun getPosts(uris: List<String>): Result<List<BlueskyFeedPost>, TrackerErr> = publicRequest<BlueskyPostsResponse>("app.bsky.feed.getPosts?uris=${uris.joinToString(",")}").mapOk { it.posts }

    private val postRegex = Regex("/([a-z0-9]{13})")
    fun extractPostKey(uri: String) = postRegex.find(uri)?.groups?.get(1)?.value
}