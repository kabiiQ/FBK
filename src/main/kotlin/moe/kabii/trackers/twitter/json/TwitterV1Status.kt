package moe.kabii.trackers.twitter.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TwitterV1Status(
    @Json(name = "extended_entities") val extended: TwitterExtendedEntities?
) : TwitterResponse {
    fun findAttachedVideo() = extended?.media
        ?.mapNotNull { media -> media.video }
        ?.firstOrNull()?.variants
        ?.filter { it.contentType == "video/mp4" && it.bitrate != null }
        ?.maxByOrNull { it.bitrate!! }?.url
}

@JsonClass(generateAdapter = true)
data class TwitterExtendedEntities(
    val media: List<TwitterExtendedMedia>?
)

@JsonClass(generateAdapter = true)
data class TwitterExtendedMedia(
    @Json(name = "video_info") val video: TwitterExtendedVideoInfo?
)

@JsonClass(generateAdapter = true)
data class TwitterExtendedVideoInfo(
    val variants: List<TwitterVideoVariant>?
)

@JsonClass(generateAdapter = true)
data class TwitterVideoVariant(
    val bitrate: Int?,
    @Json(name = "content_type") val contentType: String,
    val url: String?
)