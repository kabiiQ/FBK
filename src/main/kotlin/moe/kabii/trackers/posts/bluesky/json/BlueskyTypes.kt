package moe.kabii.trackers.posts.bluesky.json

import com.squareup.moshi.Json
import moe.kabii.trackers.posts.bluesky.BlueskyParser

// API calls involving these types return polymorphic objects which must be mapped to type by analyzing the $type field.

sealed class BlueskyPostViewBase(
    @Json(name = "\$type") val type: String
)

data class BlueskyPostView(
    val uri: String,
    val author: BlueskyAuthor,
    val record: BlueskyPostRecord,
    val embed: BlueskyEmbeddedBase?
) : BlueskyPostViewBase("app.bsky.feed.defs#postView")

data class BlueskyNotFoundPost(
    val notFound: Boolean
) : BlueskyPostViewBase("app.bsky.feed.defs#notFoundPost")


sealed class BlueskyEmbeddedBase(
    @Json(name = "\$type") val type: String
)

data class BlueskyEmbedImagesView(
    val images: List<BlueskyImage>
) : BlueskyEmbeddedBase("app.bsky.embed.images#view")

data class BlueskyEmbedVideoView(
    val playlist: String,
    val thumbnail: String?
) : BlueskyEmbeddedBase("app.bsky.embed.video#view")

data class BlueskyEmbedExternalView(
    val external: BlueskyExternalObject
) : BlueskyEmbeddedBase("app.bsky.embed.external#view")

data class BlueskyEmbedRecordView(
    val record: BlueskyEmbeddedRecordBase,
) : BlueskyEmbeddedBase("app.bsky.embed.record#view")

data class BlueskyEmbedRecordWithMediaView(
    val record: NestedRecord,
    val media: BlueskyEmbeddedBase
) : BlueskyEmbeddedBase("app.bsky.embed.recordWithMedia#view") {

    data class NestedRecord(
        val record: BlueskyEmbeddedRecordBase
    )
}


sealed class BlueskyEmbeddedRecordBase(
    @Json(name = "\$type") val type: String
) // app.bsky.embed.record#view

data class BlueskyEmbedViewRecord(
    val uri: String,
    val author: BlueskyAuthor,
    val embeds: List<BlueskyEmbeddedBase>
) : BlueskyEmbeddedRecordBase("app.bsky.embed.record#viewRecord") {
    @Transient val postId = BlueskyParser.extractPostKey(uri)
    @Transient val url = "${author.url}/post/$postId"
}

data class BlueskyEmbedNotFound(
    val notFound: Boolean
) : BlueskyEmbeddedRecordBase("app.bsky.embed.record#viewNotFound")