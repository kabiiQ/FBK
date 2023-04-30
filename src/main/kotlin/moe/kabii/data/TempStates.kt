package moe.kabii.data

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji
import moe.kabii.data.relational.twitter.TwitterFeed
import java.util.concurrent.ConcurrentHashMap

// basic non-persistent in-memory storage
object TempStates {
    data class BotReactionRemove(val messageId: Snowflake, val userId: Snowflake, val emoji: ReactionEmoji)
    val emojiRemove = mutableListOf<BotReactionRemove>()

    val emojiTLCache = mutableSetOf<Snowflake>()

    val musicPermissionWarnings = mutableMapOf<Snowflake, Boolean>()
}

object TwitterFeedCache {
    data class FeedCacheState(val initialBound: Long, val seenTweets: MutableList<Long> = mutableListOf())
    private val cache = ConcurrentHashMap<String, FeedCacheState>()
    fun getOrPut(feed: TwitterFeed): FeedCacheState = cache.getOrPut(feed.username.lowercase()) { FeedCacheState(feed.lastPulledTweet ?: 0L) }
    operator fun get(username: String) = cache[username.lowercase()]
}