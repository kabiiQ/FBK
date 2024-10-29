package moe.kabii.trackers.posts.twitter

import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.TempStates
import moe.kabii.data.TwitterFeedCache
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.data.relational.twitter.TwitterFeeds
import moe.kabii.data.relational.twitter.TwitterRetweets
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.ClientRotation
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

/**
 * Copy of NitterChecker adapted to use syndication feeds as alternate route for priority Twitter feeds
 */
class SyndicationChecker(instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : NitterChecker(instances), Runnable {
    private val nitterScope = CoroutineScope(DiscordTaskPool.streamThreads + CoroutineName("Syndication-Priority-Tweet-Intake") + SupervisorJob())

    override fun run() {
        applicationLoop {
            val start = Instant.now()
            try {
                updateFeeds(start)
            } catch(e: Exception) {
                LOG.warn("SyndicationChecker: ${e.message}")
                LOG.debug(e.stackTraceString)
            }

            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            LOG.debug("syndication delay: $delay")
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    override suspend fun updateFeeds(start: Instant) {
        LOG.debug("SyndicationChecker :: start: $start")
        // Get only priority Twitter feeds
        val feeds = propagateTransaction {
            TwitterFeed.find {
                TwitterFeeds.enabled eq true
            }.toList()
        }

        if(feeds.isEmpty() || TempStates.skipTwitter) {
            delay(Duration.ofMillis(cooldowns.minimumRepeatTime))
            return
        }
        val feedsPerClient = ceil(feeds.size.toDouble() / ClientRotation.count).toInt()
        feeds
            .chunked(feedsPerClient).withIndex()
            .map { (instanceId, feedChunk) ->
                LOG.debug("Chunk $instanceId: ${feedChunk.joinToString(", ", transform=TwitterFeed::username)} :: ${NitterParser.getInstanceUrl(instanceId)}")
                val httpClient = ClientRotation.getClientNumber(instanceId)
                nitterScope.launch {
                    try {
                        kotlinx.coroutines.time.withTimeout(Duration.ofMinutes(6)) {
                            var first = true
                            feedChunk.forEach { feed ->

                                if (!first) {
                                    delay(Duration.ofMillis(cooldowns.callDelay))
                                } else first = false

                                val targets = getActiveTargets(feed)?.ifEmpty { null }
                                    ?: return@forEach // feed untrack entirely or no target channels are currently enabled

                                val cache = TwitterFeedCache.getOrPut(feed)

                                val nitter = SyndicationParser
                                    .getFeed(feed.username, client = httpClient)
                                    ?: return@forEach

                                val (user, tweets) = nitter

                                val latest = tweets.maxOfOrNull { tweet ->
                                    // if tweet is after last posted tweet and within 2 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                                    val age = Duration.between(tweet.date, Instant.now())

                                    propagateTransaction {
                                        if (tweet.retweet) {
                                            /* Date/time and ID from Nitter feed is of ORIGINAL Tweet, not retweet event
                                            Check if this RT has already been acknowledged from this feed from our own database
                                             */
                                            val new = TwitterRetweets.checkAndUpdate(feed, tweet.id)
                                            if (!new) {
                                                return@propagateTransaction tweet.id
                                            }
                                            // if temporary switch to syndication feeds - time is accurate again
                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
                                                || age > Duration.ofHours(1)
                                                || cache.seenTweets.contains(tweet.id)
                                            ) return@propagateTransaction tweet.id
                                        } else {
                                            // if already handled or too old, skip, but do not pull tweet ID again
                                            if ((feed.lastPulledTweet ?: 0) >= tweet.id
                                                || age > Duration.ofHours(1)
        //                                                || age > Duration.ofHours(12)
                                                || cache.seenTweets.contains(tweet.id)
                                            ) return@propagateTransaction tweet.id
                                        }

                                        notifyTweet(user, tweet, targets)
                                        tweet.id
                                    }
                                }
                                if (latest != null && latest > (feed.lastPulledTweet ?: 0L)) {
                                    propagateTransaction {
                                        feed.lastPulledTweet = latest
                                    }
                                }
                            }
                        }
                    } catch(time: TimeoutCancellationException) {
                        LOG.warn("SyndicationParser routine: timeout reached")
                    } catch(e: Exception) {
                        LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                        LOG.debug(e.stackTraceString)
                    }
                }
            }
        LOG.debug("syndication exit")
    }
}