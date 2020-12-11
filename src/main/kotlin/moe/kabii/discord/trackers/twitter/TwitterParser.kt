package moe.kabii.discord.trackers.twitter

import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.Keys
import moe.kabii.discord.trackers.twitter.json.*
import moe.kabii.structure.extensions.stackTraceString
import okhttp3.Request

open class TwitterIOException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}
class TwitterRateLimitReachedException(val resetSeconds: Long, message: String) : TwitterIOException(message)

object TwitterParser {

    private val token = Keys.config[Keys.Twitter.token]

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
                    if(response.code == 429) {
                        val reset = response.headers["x-rate-limit-reset"]?.toLongOrNull()
                        throw TwitterRateLimitReachedException(reset ?: 90L, "HTTP response: ${response.message}")
                    } else {
                        LOG.error("Error calling Twitter API: $response")
                        throw TwitterIOException(response.toString())
                    }
                }
                val body = response.body!!.string()
                return try {
                    val json = MOSHI.adapter(R::class.java).fromJson(body)
                    if(json != null) json else null
                } catch (e: Exception) {
                    LOG.error("Invalid JSON provided by Twitter: ${e.message} :: $body")
                    throw TwitterIOException(e)
                }
            }
        } catch (e: Exception) {
            // actual io issue
            LOG.warn("TwitterParser: Error in Twitter call: ${e.message}")
            LOG.debug(e.stackTraceString)
            throw e
        }
    }

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun getUser(username: String): TwitterUser? = request<TwitterUserResponse>("https://api.twitter.com/2/users/by/username/$username")?.data

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun getRecentTweets(userIds: List<Long>, sinceId: Long?): Map<TwitterUser, List<TwitterTweet>> {
        val userQuery = userIds.map { user -> "from:$user" }.joinToString(" OR ")
        val since = if(sinceId != null) "&since_id=$sinceId" else ""
        val call = request<TwitterRecentTweetsResponse>("https://api.twitter.com/2/tweets/search/recent?query=$userQuery$since&tweet.fields=author_id,created_at,referenced_tweets&max_results=100&expansions=author_id")
        return if(call?.data != null && call.includes != null) { // result can just be empty if we pre-filtered tweets properly and there have been no updates to the feeds
            // twitter api returns user objects from this call in an 'expansion'
            // here we match the expanded user objects back to the tweets for easy consumption outside
            // 'includes' contains more users than we are concerned about (retweets, etc) - dont rely on those contents
            call.data.groupBy(TwitterTweet::authorId)
                .mapKeys { entry ->
                    call.includes.users.find { user -> user.id == entry.key }!!
                }
        } else mapOf()
    }
}