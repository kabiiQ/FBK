package moe.kabii.discord.trackers.twitter.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.util.URLUtil
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
    @Json(name = "author_id") val _authorRaw: String?,
    @Json(name = "created_at") val _created: String?,
    @Json(name = "referenced_tweets") val _references: List<TwitterReferences>?,
    @Json(name = "possibly_sensitive") val sensitive: Boolean?,
    val text: String?
) {

    @Transient val id: Long = requireNotNull(_id.toLongOrNull()) { "Invalid Twitter Tweet ID returned: $_id" }
    @Transient val authorId: Long = requireNotNull(_authorRaw?.toLongOrNull()) { "Invalid or missing Tweet Author: $_authorRaw" }
    @Transient val createdAt: Instant = requireNotNull(_created?.run(Instant::parse)) { "Invalid or missing Tweet creation date: $_created" }

    @Transient private val _reference = _references?.firstOrNull()
    @Transient val retweet = _reference?.type == "retweeted"
    @Transient val reply = _reference?.type == "replied_to"
    @Transient val quote = _reference?.type == "quoted"

    @Transient val notifyOption = when {
        retweet -> TwitterSettings::displayRetweet
        reply -> TwitterSettings::displayReplies
        quote -> TwitterSettings::displayQuote
        else -> TwitterSettings::displayNormalTweet
    }

    @Transient val url = URLUtil.Twitter.tweet(id.toString())
}

@JsonClass(generateAdapter = true)
data class TwitterReferences(
    val type: String
)

@JsonClass(generateAdapter = true)
data class TwitterError(
    @Json(name = "detail") val message: String
)