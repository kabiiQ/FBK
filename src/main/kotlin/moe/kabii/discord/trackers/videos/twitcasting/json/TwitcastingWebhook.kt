package moe.kabii.discord.trackers.videos.twitcasting.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.MOSHI

@JsonClass(generateAdapter = true)
data class TwitcastingWebhookRequest(
    @Json(name = "user_id") val userId: String,
    val events: List<String> = listOf("livestart", "liveend")
) {
    fun toJson() = adapter.toJson(this)

    companion object {
        private val adapter = MOSHI.adapter(TwitcastingWebhookRequest::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class TwitcastingWebhookResponse(
    @Json(name = "all_count") val totalCount: Int,
    val webhooks: List<TwitcastingWebhook>
)

@JsonClass(generateAdapter = true)
data class TwitcastingWebhook(
    @Json(name = "user_id") val userId: String,
    val event: String
)