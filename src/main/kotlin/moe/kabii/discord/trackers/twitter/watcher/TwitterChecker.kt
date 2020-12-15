package moe.kabii.discord.trackers.twitter.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TwitterSettings
import moe.kabii.data.relational.twitter.TwitterFeed
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.structure.extensions.loop
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class TwitterChecker(val discord: GatewayDiscordClient) : Runnable {

    override fun run() {
        loop {
            val start = Instant.now()

            newSuspendedTransaction {
                try {
                    // get all tracked twitter feeds
                    val feeds = TwitterFeed.all()
                        .filter { feed ->
                            if(feed.targets.empty()) {
                                LOG.info("Untracking Twitter Feed '${feed.userId} as it is not tracked in any channels.")
                                feed.delete()
                                false
                            } else true
                        }

                    // request recent tweets - call can return up to 100, around 15 users per call max should be fine ?
                    // do not expect that the 15 users will have > 100 new tweets
                    // starting with chunk size 10
                    feeds.chunked(10).toList().forEach { chunk ->
                        // pull new tweets for these accounts
                        val userIds = chunk.map(TwitterFeed::userId)

                        // pull tweets since the oldest tweet in this chunk - reduce pulling some number of tweets we already know exist
                        // (the efficiency of this depends on the users in the chunk)
                        // if any are null - don't provide any snowflake, as we want any recent tweets from that user
                        val new = chunk.any { feed -> feed.lastPulledTweet == null }
                        val sinceId = if(new) null else chunk.mapNotNull(TwitterFeed::lastPulledTweet).minOrNull()

                        TwitterParser.getRecentTweets(userIds, sinceId).forEach tweets@{ (user, tweets) ->
                            // match tweet back to feed from this chunk
                            val tweetFeed = chunk.find { feed -> feed.userId == user.id } ?: return@tweets
                            val latest = tweets.maxOf { tweet ->

                                // if tweet is after last posted tweet and within 3 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                                val age = Duration.between(tweet.createdAt, Instant.now())
                                if (tweetFeed.lastPulledTweet ?: 0 >= tweet.id || age > Duration.ofHours(2)) return@maxOf 0L

                                // send discord notifs
                                tweetFeed.targets.forEach { target ->
                                    try {
                                        // post a notif to this target
                                        val channel = discord.getChannelById(target.discordChannel.channelID.snowflake)
                                            .ofType(MessageChannel::class.java)
                                            .awaitSingle()
                                        val guildId = target.discordChannel.guild?.guildID
                                        val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
                                        val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).twitterSettings }
                                            ?: TwitterSettings()

                                        if(tweet.notifyOption.get(features)) {
                                            channel.createMessage { spec ->
                                                // todo channel setting for custom message ?

                                                val action = when {
                                                    tweet.retweet -> "retweeted \uD83D\uDD01"
                                                    tweet.reply -> "replied to a Tweet \uD83D\uDCAC"
                                                    tweet.quote -> "quoted a Tweet \uD83D\uDDE8"
                                                    else -> "posted a new Tweet"
                                                }
                                                spec.setContent("**@${user.username}** $action: https://twitter.com/${user.username}/status/${tweet.id}")
                                            }.awaitSingle()
                                        }
                                    } catch (e: Exception) {
                                        if (e is ClientException && e.status.code() == 403) {
                                            LOG.warn("Unable to send stream notification to channel '${target.discordChannel.channelID}'. Should disable feature. TwitterChecker.java")
                                        } else {
                                            LOG.warn("Error sending stream notification to channel: ${e.message}")
                                            LOG.debug(e.stackTraceString)
                                        }
                                    }
                                }
                                tweet.id // return tweet id for 'max' calculation to find the newest tweet that was returned
                            }
                            if(latest > tweetFeed.lastPulledTweet ?: 0L) {
                                newSuspendedTransaction {
                                    tweetFeed.lastPulledTweet = latest
                                }
                            }
                        }
                        //delay(2100L) todo - not required until approaching 4500 feeds tracked @ 10 chunk size
                        delay(50L)
                    }
                } catch(e: Exception) {
                    LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = 30_000L - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }
}