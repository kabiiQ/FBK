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

                    feeds.forEach { feed ->
                        val targets = feed.targets.toList()

                        if(targets.isEmpty()) {
                            LOG.info("Untracking Twitter Feed '${feed.userId} as it is not tracked in any channels.")
                            feed.delete()
                            return@forEach
                        }

                        // determine if any targets want RT or quote tweets
                        var pullRetweets = false
                        var pullQuotes = false

                        targets.forEach { target ->
                            val features = GuildConfigurations.findFeatures(target)
                            val twitter = features?.twitterSettings ?: TwitterSettings()

                            if(twitter.displayRetweet) pullRetweets = true
                            if(twitter.displayQuote) pullQuotes = true
                        }

                        val limits = TwitterParser.TwitterQueryLimits(
                            sinceId = feed.lastPulledTweet,
                            includeRT = pullRetweets,
                            includeQuote = pullQuotes
                        )
                        val recent = TwitterParser.getRecentTweets(feed.userId, limits)
                        recent ?: return@forEach
                        val (user, tweets) = recent
                        val latest = tweets.maxOf { tweet ->

                            // if tweet is after last posted tweet and within 3 hours (arbitrary - to prevent spam when initially tracking) - send discord notifs
                            val age = Duration.between(tweet.createdAt, Instant.now())
                            LOG.debug("Twitter returned quantity ${tweets.size}")
                            if (feed.lastPulledTweet ?: 0 >= tweet.id || age > Duration.ofHours(2)) return@maxOf 0L

                            // send discord notifs - check if any channels request
                            targets.forEach { target ->
                                try {
                                    // post a notif to this target
                                    val channel = discord.getChannelById(target.discordChannel.channelID.snowflake)
                                        .ofType(MessageChannel::class.java)
                                        .awaitSingle()
                                    val features = GuildConfigurations.findFeatures(target)
                                    val twitter = features?.twitterSettings ?: TwitterSettings()

                                    if(tweet.notifyOption.get(twitter)) {
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
                        if(latest > feed.lastPulledTweet ?: 0L) {
                            newSuspendedTransaction {
                                feed.lastPulledTweet = latest
                            }
                        }
                        delay(50L)
                    }
                } catch(e: Exception) {
                    LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = 45_000L - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }
}