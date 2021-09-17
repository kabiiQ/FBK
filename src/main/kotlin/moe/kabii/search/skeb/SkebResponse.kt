package moe.kabii.search.skeb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.util.extensions.capitilized

@JsonClass(generateAdapter = true)
data class SkebUser(
    val creator: Boolean,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val name: String,
    @Json(name = "screen_name") val username: String,
    val language: String?,
    @Json(name = "header_url") val header: String?,
    @Json(name = "twitter_screen_name") val twitterName: String?,

    // creator tags
    @Json(name = "acceptable") val accepting: Boolean,
    @Json(name = "nsfw_acceptable") val nsfw: Boolean,
    @Json(name = "private_acceptable") val private: Boolean,
    @Json(name = "received_works_count") val receivedRequests: Int,
    @Json(name = "genre") val _genre: String,
    @Json(name = "default_amount") val defaultAmount: Int?,

    // skebber tags
    @Json(name = "sent_public_works_count") val sentRequests: Int,
) {
    @Transient val genre = _genre.run { if(this == "art") "Artwork" else capitilized() }
}