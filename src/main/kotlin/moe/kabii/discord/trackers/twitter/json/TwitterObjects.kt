package moe.kabii.discord.trackers.twitter.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.util.constants.URLUtil
import java.time.Instant

@JsonClass(generateAdapter = true)
data class TwitterUser(
    @Json(name = "id") val _id: String,
    val name: String,
    val username: String,
    @Json(name = "profile_image_url") val profileImage: String?
) {
    @Transient val id: Long = requireNotNull(_id.toLongOrNull()) { "Invalid Twitter User ID returned: $_id" }

    @Transient val url = URLUtil.Twitter.feed(id.toString())
}

@JsonClass(generateAdapter = true)
data class TwitterTweet(
    @Json(name = "id") val _id: String,
    @Json(name = "created_at") val _created: String,
    @Json(name = "referenced_tweets") val _references: List<TwitterReferences>?,
    @Json(name = "attachments") val _attachments: TweetAttachmentsObject?,
    @Json(name = "author_id") val _authorId: String,
    @Json(name = "possibly_sensitive") val sensitive: Boolean?,
    val entities: TwitterEntities?,
    @Json(name = "text") val _text: String
) {

    @Transient val id: Long = requireNotNull(_id.toLongOrNull()) { "Invalid Twitter Tweet ID returned: $_id" }
    @Transient val createdAt: Instant = requireNotNull(Instant.parse(_created)) { "Invalid or missing Tweet creation date: $_created" }
    @Transient val authorId = _authorId.toLong()

    @Transient private val _reference = _references?.firstOrNull()
    @Transient val retweet = _reference?.type == "retweeted"
    @Transient val reply = _reference?.type == "replied_to"
    @Transient val quote = _reference?.type == "quoted"

    private val rtRegex = Regex("RT @${TwitterParser.twitterUsernameRegex}: ")
    @Transient val text = _text.replaceFirst(rtRegex, "")

    @Transient lateinit var attachments: MutableList<TwitterMediaObject>
    @Transient lateinit var references: MutableList<TwitterTweet>
    @Transient var author: TwitterUser? = null

    @Transient val notifyOption = when {
        retweet -> TwitterSettings::displayRetweet
        reply -> TwitterSettings::displayReplies
        quote -> TwitterSettings::displayQuote
        else -> TwitterSettings::displayNormalTweet
    }

    @Transient val url = URLUtil.Twitter.tweet(id.toString())
}

@JsonClass(generateAdapter = true)
data class TweetAttachmentsObject(
    @Json(name = "media_keys") val mediaKeys: List<String>?
)

enum class TwitterMediaType(val matchName: String) {
    GIF("animated_gif"),
    VID("video"),
    PIC("photo");

    companion object {
        fun of(type: String) = values().find { it.matchName == type } ?: PIC
    }
}

@JsonClass(generateAdapter = true)
data class TwitterMediaObject(
    @Json(name = "media_key") val key: String,
    @Json(name = "type") val _type: String,
    @Json(name = "url") val _url: String?,
    @Json(name = "preview_image_url") val _previewUrl: String?
) {
    @Transient val url = _url ?: _previewUrl
    @Transient val type = TwitterMediaType.of(_type)
}

@JsonClass(generateAdapter = true)
data class TwitterReferences(
    val type: String,
    @Json(name = "id") val _id: String
) {
    @Transient val referencedTweetId = _id.toLong()
}

@JsonClass(generateAdapter = true)
data class TwitterError(
    @Json(name = "detail") val message: String
)

@JsonClass(generateAdapter = true)
data class TwitterEntities(
    val urls: List<TwitterUrlEntity>?
)

@JsonClass(generateAdapter = true)
data class TwitterUrlEntity(
    val images: List<TwitterUrlImage>?
)

@JsonClass(generateAdapter = true)
data class TwitterUrlImage(
    val url: String
)

