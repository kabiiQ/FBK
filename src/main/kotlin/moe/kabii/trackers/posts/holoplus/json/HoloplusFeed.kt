package moe.kabii.trackers.posts.holoplus.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class HoloplusFeed(
    val items: List<HoloplusFeedEntry>
)

@JsonClass(generateAdapter = true)
data class HoloplusFeedEntry(
    @Json(name = "id") val channelId: String,
    @Json(name = "latest_thread") val thread: HoloplusThread,
    val talent: HoloplusTalentInfo
)

@JsonClass(generateAdapter = true)
data class HoloplusThread(
    val id: String,
    @Json(name = "created_at") val _createdAt: Long
) {
    @Transient val createdAt = Instant.ofEpochSecond(_createdAt)
}

@JsonClass(generateAdapter = true)
data class HoloplusTalentInfo(
    val id: String,
    @Json(name = "icon_url") val icon: String
)
