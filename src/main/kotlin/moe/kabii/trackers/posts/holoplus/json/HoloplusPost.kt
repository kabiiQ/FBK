package moe.kabii.trackers.posts.holoplus.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.constants.URLUtil
import java.time.Instant

@JsonClass(generateAdapter = true)
data class HoloplusChannel(
    val items: List<HoloplusPost>
)

@JsonClass(generateAdapter = true)
data class HoloplusPost(
    val id: String,
    @Json(name = "created_at") val _createdAt: Long,
    @Json(name = "image_urls") val imageUrls: List<String> = listOf(),
    val talents: List<HoloplusTalent>,
    val title: String,
    val body: String,
    @Json(name = "original_language") val language: String?,
    val translations: HoloplusPostTranslations?,
    @Json(name = "voice_clip") val voiceClip: HoloplusVoiceClip?
) {
    @Transient val createdAt = Instant.ofEpochSecond(_createdAt)
    @Transient val url = URLUtil.Holoplus.thread(id)
}

@JsonClass(generateAdapter = true)
data class HoloplusPostTranslations(
    val ja: HoloplusTranslation,
    val en: HoloplusTranslation,
    val id: HoloplusTranslation
)

@JsonClass(generateAdapter = true)
data class HoloplusTranslation(
    val title: String,
    val body: String
)

@JsonClass(generateAdapter = true)
data class HoloplusVoiceClip(
    val url: String
)