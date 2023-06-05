package moe.kabii.trackers.videos.kick.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil

@JsonClass(generateAdapter = true)
data class KickChannel(
    val id: Int,
    val slug: String,
    @Json(name = "vod_enabled") val vod: Boolean,
    val livestream: KickLivestream?,
    val user: KickUser
) {
    @Transient val url = URLUtil.StreamingSites.Kick.channelByName(slug)
}

@JsonClass(generateAdapter = true)
data class KickLivestream(
    @Json(name = "created_at") val _createdAt: String,
    @Json(name = "session_title") val title: String?,
    @Json(name = "is_live") val live: Boolean,
    val viewers: Int,
    val thumbnail: KickThumbnail?,
    val categories: List<KickCategory>
)

@JsonClass(generateAdapter = true)
data class KickUser(
    val username: String
)

@JsonClass(generateAdapter = true)
data class KickThumbnail(
    val url: String?
)

@JsonClass(generateAdapter = true)
data class KickCategory(
    val name: String
)