package moe.kabii.trackers.nitter

import moe.kabii.data.mongodb.guilds.TwitterSettings
import java.time.Instant

data class NitterData(
    val user: NitterUser,
    val tweets: List<NitterTweet>
)

data class NitterUser(
    val username: String,
    val name: String,
    val avatar: String
)

data class NitterTweet(
    val id: Long,
    val text: String,
    val html: String,
    val date: Instant,
    val url: String,

    val images: List<String>,
    val hasVideo: Boolean,
    val retweetOf: String?,
    val reply: Boolean,
    val quote: Boolean
) {
    val retweet = retweetOf != null

    val notifyOption = when {
        retweet -> TwitterSettings::displayRetweet
        else -> TwitterSettings::displayNormalTweet
    }
}