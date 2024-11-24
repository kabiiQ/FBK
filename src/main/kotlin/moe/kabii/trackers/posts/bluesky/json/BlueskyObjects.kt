package moe.kabii.trackers.posts.bluesky.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil
import java.time.Instant

@JsonClass(generateAdapter = true)
data class BlueskyAuthor(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatar: String?
) {
    val url = URLUtil.Bluesky.feedUsername(handle)
}

@JsonClass(generateAdapter = true)
data class BlueskyFeedPost(
    val uri: String,
    val author: BlueskyAuthor,
    val record: BlueskyPostRecord,
    val embed: BlueskyEmbeddedBase?
)

@JsonClass(generateAdapter = true)
data class BlueskyPostRecord(
    @Json(name = "createdAt") val _createdAt: String,
    val langs: List<String>?,
    val text: String
) {
    @Transient val createdAt = Instant.parse(_createdAt)
}

// Post "reason" is polymorphic but only "reasonRepost" provides any useful information, so just treating is as nullable here is fine.
@JsonClass(generateAdapter = true)
data class BlueskyPostReason(
    val by: BlueskyAuthor?,
    @Json(name = "indexedAt") val _indexedAt: String
) {
    @Transient val indexedAt = Instant.parse(_indexedAt)
}

@JsonClass(generateAdapter = true)
data class BlueskyImage(
    val thumb: String,
    @Json(name = "fullsize") val fullSize: String
)

@JsonClass(generateAdapter = true)
data class BlueskyExternalObject(
    val title: String,
    val description: String,
    val thumb: String?
)