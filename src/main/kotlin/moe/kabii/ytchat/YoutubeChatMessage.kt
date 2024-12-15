package moe.kabii.ytchat

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YTChatMessage(
    val author: YoutubeChatAuthor,
    val type: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class YoutubeChatAuthor(
    @Json(name = "isChatSponsor") val member: Boolean,
    @Json(name = "isChatModerator") val moderator: Boolean,
    @Json(name = "isChatOwner") val owner: Boolean,
    val channelId: String,
    val channelUrl: String,
    val name: String,
    val imageUrl: String?
) {
    @Transient val staff = owner || moderator
}
