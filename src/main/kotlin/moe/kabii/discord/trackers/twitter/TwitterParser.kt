package moe.kabii.discord.trackers.twitter

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.trackers.twitter.json.*
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import java.time.Duration
import java.time.Instant

open class TwitterIOException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}
class TwitterDateTimeUpdateException(val snowflake: Long) : TwitterIOException("Bad request: since_id is too old")
class TwitterRateLimitReachedException(val reset: Duration, message: String) : TwitterIOException(message)

object TwitterParser {

    private val token = Keys.config[Keys.Twitter.token]

    val sinceIdError = Regex("Please use a 'since_id' that is larger than (\\d{19,})")
    val twitterUsernameRegex = Regex("[a-zA-Z0-9_]{4,15}")

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    private inline fun <reified R: TwitterResponse> request(requestStr: String): R? {

        val request = Request.Builder()
            .get()
            .url(requestStr)
            .header("User-Agent", "srkmfbk/1.0")
            .header("Authorization", "Bearer $token")
            .build()

        try {
            OkHTTP.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        val reset = response.headers["x-rate-limit-reset"]?.toLongOrNull()?.let { resetTime ->
                            Duration.between(Instant.now(), Instant.ofEpochSecond(resetTime))
                        }
                        throw TwitterRateLimitReachedException(
                            reset ?: Duration.ofMillis(1000L), "HTTP response: ${response.code}"
                        )
                    } else if(response.code == 400) {
                        val body = response.body!!.string()
                        val errorJson = TwitterBadRequestResponse.adapter.fromJson(body)
                        if(errorJson != null) {
                            // weird issue here: the since_id we provide to Twitter must be within 1 week. but if their last tweet was older than this, we don't want to pull any tweets at all until there are new ones
                            val sinceIdMatch = errorJson.errors.firstOrNull()?.message?.run(sinceIdError::find)
                            if(sinceIdMatch != null) {
                                val newId = sinceIdMatch.groups[1]!!.value.toLong()
                                throw TwitterDateTimeUpdateException(newId)
                            }
                        }
                    } else {
                        LOG.error("Error calling Twitter API: $response")
                    }
                    throw TwitterIOException(response.toString())
                }
                val body = response.body!!.string()
                return try {
                    val json = MOSHI.adapter(R::class.java).fromJson(body)
                    if (json != null) json else null
                } catch (e: Exception) {
                    LOG.error("Invalid JSON provided by Twitter: ${e.message} :: $body")
                    throw TwitterIOException(e)
                }
            }
        } catch (e: Exception) {
            // logging and rethrow
            LOG.warn("TwitterParser: Error in Twitter call: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun getUser(username: String): TwitterUser? = request<TwitterUserResponse>("https://api.twitter.com/2/users/by/username/$username")?.data

    data class TwitterRecentTweets(val user: TwitterUser, val tweets: List<TwitterTweet>)

    data class TwitterQueryLimits(
        val tweetLimit: Long = 15,
        val sinceId: Long? = null,
        val includeRT: Boolean = false,
        val includeQuote: Boolean = false
    )

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun getRecentTweets(userId: Long, queryLimits: TwitterQueryLimits?): TwitterRecentTweets? {
        val limits = queryLimits ?: TwitterQueryLimits()
        val query = StringBuilder("https://api.twitter.com/2/tweets/search/recent?query=")
            .append("from:$userId")
        if(!limits.includeRT) query.append(" -is:retweet ")
        if(!limits.includeQuote) query.append(" -is:quote ")
        if(limits.sinceId != null) query.append("&since_id=${limits.sinceId}")
        query.append("&tweet.fields=created_at,referenced_tweets,possibly_sensitive,text,entities")
        query.append("&max_results=${limits.tweetLimit}")
        query.append("&expansions=author_id,attachments.media_keys,referenced_tweets.id.author_id")
        query.append("&user.fields=profile_image_url")
        query.append("&media.fields=preview_image_url,url")
        val call = request<TwitterRecentTweetsResponse>(query.toString())
        return if(call?.data != null && call.includes != null) {
            val tweets = call.data.onEach { tweet -> mapTweetIncludes(tweet, call.includes) }
            TwitterRecentTweets(user = call.includes.users.first(), tweets = tweets)
        } else null
    }

    private fun mapTweetIncludes(tweet: TwitterTweet, includes: TwitterExpandedResponse) {
        tweet.attachments = mutableListOf()
        tweet._attachments?.mediaKeys?.mapNotNullTo(tweet.attachments) { key ->
            includes.media?.find { media -> media.key == key }
        }
        tweet.references = mutableListOf()
        tweet._references?.mapNotNullTo(tweet.references) { reference ->
            includes.tweets?.find { included -> reference.referencedTweetId == included.id }
        }
        includes.tweets.orEmpty().plus(tweet).forEach { tw ->
            tw.author = includes.users.firstOrNull { includedUser -> tw.authorId == includedUser.id }
        }
    }
}