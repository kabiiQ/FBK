package moe.kabii.trackers.twitter

import discord4j.rest.util.Color
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.OkHTTP
import moe.kabii.data.flat.Keys
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterStreamRule
import moe.kabii.discord.trackers.twitter.json.TwitterSpace
import moe.kabii.discord.trackers.twitter.json.TwitterSpaceMultiResponse
import moe.kabii.discord.trackers.twitter.json.TwitterSpaceSingleResponse
import moe.kabii.newRequestBuilder
import moe.kabii.trackers.twitter.json.*
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import okhttp3.Request
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

open class TwitterIOException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}
class TwitterDateTimeUpdateException(val snowflake: Long) : TwitterIOException("Bad request: since_id is too old")
class TwitterRateLimitReachedException(val reset: Duration, message: String) : TwitterIOException(message)

object TwitterParser {
    val color = Color.of(1942002)

    val token = Keys.config[Keys.Twitter.token]

    val sinceIdError = Regex("Please use a 'since_id' that is larger than (\\d{19,})")
    val twitterUsernameRegex = Regex("[a-zA-Z0-9_]{4,15}")

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    private inline fun <reified R: TwitterResponse> get(requestStr: String): R? = newRequestBuilder().get().url(requestStr).run(::doRequest)

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    private inline fun <reified R: TwitterResponse> doRequest(builder: Request.Builder): R? {
        try {
            val request = builder.header("Authorization", "Bearer $token").build()
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
                    MOSHI.adapter(R::class.java).fromJson(body)
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
    fun getUser(username: String): TwitterUser? = get<TwitterUserResponse>("https://api.twitter.com/2/users/by/username/$username")?.data

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun getUser(userId: Long): TwitterUser? = get<TwitterUserResponse>("https://api.twitter.com/2/users/$userId")?.data

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
        query.append("&max_results=${limits.tweetLimit}")
        applyTweetQueryParams(query)
        val call = get<TwitterTweetResponse>(query.toString())
        return if(call?.data != null && call.includes != null) {
            TwitterRecentTweets(user = call.includes.users.first(), tweets = call.data)
        } else null
    }

    fun applyTweetQueryParams(query: StringBuilder, first: Boolean = false) {
        val char = if(first) '?' else '&'
        query.append("${char}tweet.fields=created_at,referenced_tweets,possibly_sensitive,text,entities")
        query.append("&expansions=author_id,attachments.media_keys,referenced_tweets.id.author_id")
        query.append("&user.fields=profile_image_url")
        query.append("&media.fields=preview_image_url,url")
    }

    @Throws(TwitterIOException::class, TwitterRateLimitReachedException::class)
    fun updateStreamRules(update: TwitterRuleRequest): TwitterRuleResponse? {
        val request = newRequestBuilder()
            .url("https://api.twitter.com/2/tweets/search/stream/rules")
            .post(update.toRequestBody())
        return doRequest(request)
    }

    @WithinExposedContext
    @Throws(TwitterIOException::class)
    suspend fun deleteRule(rule: TwitterStreamRule): TwitterRuleResponse {
        val deletion = updateStreamRules(TwitterRuleRequest.delete(rule.ruleId))
        if(deletion?.meta?.summary?.notDeleted != 0) {
            throw TwitterIOException("Twitter rule deletion failed: $rule")
        }
        propagateTransaction {
            rule.delete()
        }
        return deletion
    }

    @WithinExposedContext
    suspend fun createRule(feeds: List<TwitterFeed>): TwitterRuleResponse {
        val rule = feeds.joinToString(" OR ") { feed -> "from:${feed.userId}" }
        val twitterRule = updateStreamRules(TwitterRuleRequest.add(rule))
        val ruleId = twitterRule?.data?.firstOrNull()?.ruleId
        if(twitterRule == null || ruleId == null) throw TwitterIOException("Twitter rule creation failed: $feeds")
        propagateTransaction {
            val dbRule = transaction {
                TwitterStreamRule.insert(ruleId)
            }

            feeds.onEach { feed ->
                feed.streamRule = dbRule
            }
        }
        return twitterRule
    }

    fun getV1Tweet(tweetId: String): TwitterV1Status? = get("https://api.twitter.com/1.1/statuses/show/$tweetId.json")

    const val spaceQueryParams = "space.fields=creator_id,scheduled_start,started_at,ended_at,title,participant_count&expansions=host_ids&user.fields=profile_image_url"

    fun getSpace(spaceId: String?): TwitterSpace? = get<TwitterSpaceSingleResponse>("https://api.twitter.com/2/spaces/$spaceId?$spaceQueryParams")?.data

    fun getSpaces(spaceIds: List<String>): List<TwitterSpace> {
        if(spaceIds.size > 100) throw IllegalArgumentException("max ids: 100")
        val ids = spaceIds.joinToString(",")
        return get<TwitterSpaceMultiResponse>("https://api.twitter.com/2/spaces?ids=$ids&$spaceQueryParams")?.data.orEmpty()
    }

    fun getSpacesByCreators(creatorIds: List<String>): List<TwitterSpace> {
        if(creatorIds.size > 100) throw IllegalArgumentException("max ids: 100")
        val ids = creatorIds.joinToString(",")
        return get<TwitterSpaceMultiResponse>("https://api.twitter.com/2/spaces/by/creator_ids?user_ids=$ids&$spaceQueryParams")?.data.orEmpty()
    }
}