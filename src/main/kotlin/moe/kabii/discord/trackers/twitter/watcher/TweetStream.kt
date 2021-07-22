package moe.kabii.discord.trackers.twitter.watcher

import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.MOSHI
import moe.kabii.USERAGENT
import moe.kabii.data.TwitterFeedCache
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.discord.trackers.twitter.json.TwitterSingleTweetData
import moe.kabii.discord.util.Metadata
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.min

class TweetStream(val twitter: TwitterChecker) : Runnable {

    private val intakeContext = CoroutineScope(DiscordTaskPool.twitterIntakeThread + CoroutineName("TwitterStream-Notifier") + SupervisorJob())
    private val tweetAdapter = MOSHI.adapter(TwitterSingleTweetData::class.java)

    override fun run() {
        if(!Metadata.host) return

        var networkError = 250L
        var httpError = 5_000L
        var rateLimitError = 60_000L
        var delay: Long

        val urlBuilder = StringBuilder("https://api.twitter.com/2/tweets/search/stream")
        TwitterParser.applyTweetQueryParams(urlBuilder, first = true)
        val streamUrl = urlBuilder.toString()
        applicationLoop {

            // open streaming connection
            try {
                val conn = URL(streamUrl).openConnection() as HttpsURLConnection
                conn.readTimeout = 40_500 // two missed heart beats = disconnect
                conn.setRequestProperty("User-Agent", USERAGENT)
                conn.setRequestProperty("Authorization", "Bearer ${TwitterParser.token}")

                if(conn.responseCode != 200) {
                    if(conn.responseCode == 429) {
                        delay = rateLimitError
                        rateLimitError *= 2
                    } else {
                        delay = httpError
                        httpError = min((httpError * 2), 320_000)
                    }
                    delay(delay)
                    LOG.info("Twitter stream returned status code: ${conn.responseCode}. Attempting reconnection in ${delay}ms")
                    return@applicationLoop
                }

                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->

                    // connected. reset timeouts
                    networkError = 250
                    httpError = 5_000
                    rateLimitError = 60_000

                    LOG.info("Twitter stream opened, listening for data")

                    reader.lines()
                        .filter(String::isNotBlank) // keep alive signals \r\n
                        .forEach { line ->

                        // process data
                        LOG.info("TwitterStream: $line")
                        intakeContext.launch {
                            try {
                                val response = tweetAdapter.fromJson(line)

                                if(response?.data != null && response.includes != null) {
                                    LOG.trace("Decoded tweet from stream: $response")

                                    val user = response.includes.users.first()
                                    val tweet = response.data

                                    propagateTransaction {
                                        val feed = TwitterFeed
                                            .find { TwitterFeeds.userId eq user.id }
                                            .firstOrNull()
                                        val targets = feed?.run { twitter.getActiveTargets(this) }
                                            ?: return@propagateTransaction

                                        feed.lastKnownUsername = user.username

                                        // if already handled, skip
                                        val cache = TwitterFeedCache.getOrPut(feed)
                                        if(tweet.id < cache.initialBound || cache.seenTweets.contains(tweet.id)) return@propagateTransaction

                                        if(tweet.id > feed.lastPulledTweet ?: 0L) {
                                            feed.lastPulledTweet = tweet.id
                                        }

                                        twitter.notifyTweet(user, tweet, targets)
                                    }
                                }
                            } catch(e: Exception) {
                                LOG.warn("Unable to decode Tweet: ${e.message}")
                            }
                        }
                    }
                    delay(1_000L)
                }
            } catch(timeout: SocketTimeoutException) {
                LOG.debug("Twitter stream disconnected: timeout")
                LOG.trace(timeout.stackTraceString)
                delay(1_000L)
            } catch(io: Exception) {
                LOG.info("Twitter stream disconnected: network error :: ${io.message}")
                LOG.debug(io.stackTraceString)
                delay(networkError)
                networkError = min((networkError + 250), 16_000)
            }
        }
    }
}