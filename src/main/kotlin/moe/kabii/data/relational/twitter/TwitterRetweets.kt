package moe.kabii.data.relational.twitter

import moe.kabii.util.extensions.RequiresExposedContext
import org.jetbrains.exposed.sql.*

/**
    Database table to work around being unable to identify retweet date/time coming from Nitter
    Existence of a row for a Feed/Tweet ID combo means it has been seen before and should not be posted.
 */
object TwitterRetweetHistory : Table() {
    val feed = reference("retweeted_by", TwitterFeeds, ReferenceOption.CASCADE)
    val tweetId = long("retweet_of")

    override val primaryKey = PrimaryKey(feed, tweetId)
}

/**
 * Util object called to check/update history of known retweets.
 */
object TwitterRetweets {
    @RequiresExposedContext
    private fun checkKnown(feed: TwitterFeed, tweetId: Long) = TwitterRetweetHistory.select {
        TwitterRetweetHistory.feed eq feed.id and
                (TwitterRetweetHistory.tweetId eq tweetId)
    }.any()

    @RequiresExposedContext
    private fun acknowledge(dbFeed: TwitterFeed, retweetId: Long) {
        TwitterRetweetHistory.insertIgnore { new ->
            new[feed] = dbFeed.id
            new[tweetId] = retweetId
        }
    }

    /**
     * Records a Retweet after it has been seen and handled already for a specific feed.
     * @param feed The feed that the Retweet is associated with.
     * @param retweetedId The ID of the original Tweet that was retweeted by the feed.
     * @return true if the retweet has been newly recorded (and therefore sent to notification)
     */
    @RequiresExposedContext
    fun checkAndUpdate(feed: TwitterFeed, retweetedId: Long): Boolean {
        val new = !checkKnown(feed, retweetedId)
        if(new) acknowledge(feed, retweetedId)
        return new
    }
}