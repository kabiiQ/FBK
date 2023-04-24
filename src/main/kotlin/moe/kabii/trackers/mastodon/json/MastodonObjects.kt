package moe.kabii.trackers.mastodon.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.data.mongodb.guilds.MastodonSettings
import java.time.Instant

@JsonClass(generateAdapter = true)
data class MastodonStatus(
    val id: String,
    @Json(name = "created_at") val _createdAt: String,
    @Json(name = "in_reply_to_id") val replyToId: String?,
    @Json(name = "in_reply_to_account_id") val replyToAccount: String?,
    val sensitive: Boolean,
    val language: String?,
    val uri: String,
    val url: String?,
    val content: String,
    val reblog: MastodonStatus?,
    val account: MastodonAccount,
    @Json(name = "media_attachments") val mediaAttachments: List<MastodonMedia>,
    val card: MastodonCard?
) {
    @Transient val createdAt: Instant = requireNotNull(Instant.parse(_createdAt)) { "Invalid or missing status creation date: $_createdAt" }

    @Transient val isReblog = reblog != null
    @Transient val isReply = replyToId != null
    @Transient val normal = !(isReblog || isReply)

    @Transient val notifyOption = when {
        isReblog -> MastodonSettings::postReblog
        isReply -> MastodonSettings::postReply
        else -> MastodonSettings::postStatus
    }
}

@JsonClass(generateAdapter = true)
data class MastodonAccount(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    val url: String,
    val avatar: String
)

@JsonClass(generateAdapter = true)
data class MastodonMedia(
    val type: String,
    val url: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class MastodonCard(
    val url: String,
    val title: String,
    val type: String,
    @Json(name = "provider_name") val providerName: String,
    val image: String

)