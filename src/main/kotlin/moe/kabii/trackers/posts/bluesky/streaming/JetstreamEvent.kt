package moe.kabii.trackers.posts.bluesky.streaming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.trackers.posts.bluesky.xrpc.json.BlueskyReplyLinks
import java.time.Instant

@JsonClass(generateAdapter = true)
data class JetstreamEvent(
    val did: String,
    @Json(name = "time_us") val timestamp: Long,
    @Json(name = "type") val _type: String,
    val commit: JetstreamCommit?
) {
    @Transient val type = Type[_type]

    enum class Type(val key: String) {
        COMMIT("com"),
        IDENTITY("id"),
        ACCOUNT("acc");

        companion object {
            operator fun get(key: String) = Type.entries.find { type -> type.key == key }
        }
    }
}

@JsonClass(generateAdapter = true)
data class JetstreamCommit(
    @Json(name = "type") val _op: String,
    val collection: String,
    val rkey: String,
    val record: JetstreamPostRecord?
) {
    @Transient val operation = Operation[_op]

    enum class Operation(val key: String) {
        CREATE("c"),
        UPDATE("u"),
        DELETE("d");

        companion object {
            operator fun get(key: String) = Operation.entries.find { op -> op.key == key }
        }
    }
}

@JsonClass(generateAdapter = true)
data class JetstreamPostRecord(
    @Json(name = "createdAt") val _createdAt: String?,
    val reply: BlueskyReplyLinks? // "un-hydrated" reply link info
) {
    @Transient val createdAt = _createdAt?.run(Instant::parse)
}
