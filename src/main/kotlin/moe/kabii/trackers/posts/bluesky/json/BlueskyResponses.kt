package moe.kabii.trackers.posts.bluesky.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class BlueskyErrorResponse(
    val error: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class BlueskyFeedResponse(
    val feed: List<BlueskyPost>
)

@JsonClass(generateAdapter = true)
data class BlueskyPost(
    val post: BlueskyPostView,
    val reply: BlueskyReply?,
    val reason: BlueskyPostReason?
)

@JsonClass(generateAdapter = true)
data class BlueskyReply(
    val root: BlueskyPostView,
    val parent: BlueskyPostView
)

@JsonClass(generateAdapter = true)
data class BlueskyPostReason(
    val by: BlueskyAuthor?
)

@JsonClass(generateAdapter = true)
data class BlueskyPostView(
    val uri: String,
    val author: BlueskyAuthor,
    val record: BlueskyPostDetail
)

@JsonClass(generateAdapter = true)
data class BlueskyAuthor(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatar: String?
)

@JsonClass(generateAdapter = true)
data class BlueskyPostDetail(
    @Json(name = "createdAt") val _createdAt: String,
    val langs: List<String>?,
    val text: String
) {
    @Transient val createdAt = Instant.parse(_createdAt)
}