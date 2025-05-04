package moe.kabii.trackers.posts.twitter

import moe.kabii.data.mongodb.guilds.PostsSettings
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
    val videos: List<String>,
    val retweetOf: String?,
    val replyTo: String?,
    val quoteOf: String?,
    val quoteOfTweet: Long?
) {
    val retweet = retweetOf != null
    val reply = replyTo != null
    val quote = quoteOfTweet != null

    val quoteTweetUrl: String
    get() = "https://twitter.com/$quoteOf/status/$quoteOfTweet"

    val notifyOption = when {
        retweet -> PostsSettings::displayReposts
//        reply -> PostsSettings::displayReplies
        quote -> PostsSettings::displayQuote
        else -> PostsSettings::displayNormalPosts
    }

    val mentionOption = when {
        retweet -> PostsSettings::mentionReposts
        quote -> PostsSettings::mentionQuotes
        else -> PostsSettings::mentionNormalPosts
    }
}