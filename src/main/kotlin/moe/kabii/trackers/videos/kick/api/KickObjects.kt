package moe.kabii.trackers.videos.kick.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    @Json(name = "categories") val _categories: List<KickCategory>
) {
    companion object {
        // different format for 'livestream' created_at field
        // 2023-06-29 18:00:00
        private val liveStreamDateTimeFormat = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.from(ZoneOffset.UTC))
    }

    @Transient val createdAt = Instant.from(liveStreamDateTimeFormat.parse(_createdAt))
    @Transient val categories = _categories
        .joinToString(", ", transform = KickCategory::name)
        .ifBlank { "none" }
}

@JsonClass(generateAdapter = true)
data class KickUser(
    val username: String,
    @Json(name = "profile_pic") val avatarUrl: String
)

@JsonClass(generateAdapter = true)
data class KickThumbnail(
    val url: String?
)

@JsonClass(generateAdapter = true)
data class KickCategory(
    val name: String
)