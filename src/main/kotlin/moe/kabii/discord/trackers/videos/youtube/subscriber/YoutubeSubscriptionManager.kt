package moe.kabii.discord.trackers.videos.youtube.subscriber

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.delay
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.FeedSubscription
import moe.kabii.data.relational.streams.youtube.FeedSubscriptions
import moe.kabii.discord.trackers.videos.StreamWatcher
import moe.kabii.structure.extensions.loop
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant

class YoutubeSubscriptionManager(discord: GatewayDiscordClient) : Runnable, StreamWatcher(discord) {

    val subscriber = YoutubeFeedSubscriber()
    private val listener = YoutubeFeedListener(this)

    var currentSubscriptions = setOf<String>()

    companion object {
        val maxSubscriptionInterval = Duration.standardDays(2)
    }

    override fun run() {
        // start callback server
        listener.server.start()

        val cutOff = DateTime.now().minus(maxSubscriptionInterval)
        currentSubscriptions = transaction {
            FeedSubscription.find {
                FeedSubscriptions.lastSubscription greaterEq cutOff
            }.map { feed ->
                feed.ytChannel.siteChannelID
            }.toSet()
        }

        loop {
            newSuspendedTransaction {
                // subscribe to updates for all channels with active targets
                val ytChannels = TrackedStreams.StreamChannel.find {
                    TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE
                }
                ytChannels.forEach { channel ->
                    if (!channel.targets.empty()) {
                        val subscription = FeedSubscription.find {
                            FeedSubscriptions.ytChannel eq channel.id
                        }.firstOrNull()

                        // only make actual call to google if last call is old - but still maintain current list in memory
                        if(subscription == null) {
                            val callTopic = subscriber.subscribe(channel.siteChannelID, call = true)
                            if(callTopic != null) {
                                FeedSubscription.new {
                                    this.ytChannel = channel
                                    this.lastSubscription = DateTime.now()
                                }
                            }
                        } else {
                            val lastSub = subscription.lastSubscription
                            val expired = !currentSubscriptions.contains(channel.siteChannelID) || Duration(lastSub, Instant.now()) >= maxSubscriptionInterval
                            val callTopic = subscriber.subscribe(channel.siteChannelID, expired)
                            if(callTopic != null) {
                                subscription.lastSubscription = DateTime.now()
                            }
                        }
                        currentSubscriptions = currentSubscriptions + channel.siteChannelID
                    }
                }
            }

            // no un-necessary calls are made here, we can run this on a fairly low interval
            delay(30_000L)
        }
    }
}