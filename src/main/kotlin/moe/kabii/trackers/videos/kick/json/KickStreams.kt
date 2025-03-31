package moe.kabii.trackers.videos.kick.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.trackers.videos.kick.parser.KickStreamInfo
import java.time.Instant

object KickSignature {
    @JsonClass(generateAdapter = true)
    data class Response(
        val data: PublicKey
    )

    @JsonClass(generateAdapter = true)
    data class PublicKey(
        @Json(name = "public_key") val key: String
    )
}

@JsonClass(generateAdapter = true)
data class KickChannelResponse(
    val data: List<KickChannel>,
    val message: String
)

@JsonClass(generateAdapter = true)
data class KickChannel(
    @Json(name = "broadcaster_user_id") val userId: Long,
    val slug: String,
    val stream: KickStream,
    @Json(name = "stream_title") val streamTitle: String,
    val category: KickCategory
) {
    fun asStreamInfo() = KickStreamInfo(userId, slug, streamTitle, category, stream.live, stream.startTime, stream.viewers)
}

@JsonClass(generateAdapter = true)
data class KickCategory(
    val name: String,
    val thumbnail: String
) {
    constructor() : this("Nothing", "")
}

@JsonClass(generateAdapter = true)
data class KickStream(
    @Json(name = "is_live") val live: Boolean,
    @Json(name = "start_time") val _startTime: String,
    @Json(name = "viewer_count") val viewers: Int
) {
    @Transient val startTime = Instant.parse(_startTime)
}