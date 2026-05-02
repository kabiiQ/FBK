package moe.kabii.trackers.posts.holoplus.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HoloplusTalent(
    val id: String,
    val name: String,
    @Json(name = "key_name") val keyName: String,
    @Json(name = "img_url") val image: String
)

