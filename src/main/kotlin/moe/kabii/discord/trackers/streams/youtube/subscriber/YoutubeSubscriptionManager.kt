package moe.kabii.discord.trackers.streams.youtube.subscriber

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.FeedSubscription
import moe.kabii.data.relational.streams.youtube.FeedSubscriptions
import moe.kabii.discord.trackers.streams.StreamWatcher
import moe.kabii.structure.extensions.loop
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant

class YoutubeSubscriptionManager(discord: GatewayDiscordClient) : Runnable, StreamWatcher(discord) {

    private val subscriber = YoutubeFeedSubscriber()
    private val listener = YoutubeFeedListener(this)

    var currentSubscriptions = setOf<String>()

    companion object {
        val maxSubscriptionInterval = Duration.standardDays(2)
    }

    override fun run() {
        // start callback server
        listener.server.start()

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
                        val topic = if(subscription == null) {
                            FeedSubscription.new {
                                this.ytChannel = channel
                                this.lastSubscription = DateTime.now()
                            }
                            subscriber.subscribe(channel.siteChannelID, call = true)
                        } else {
                            val lastSub = subscription.lastSubscription
                            val expired = Duration(lastSub, Instant.now()) >= maxSubscriptionInterval
                            subscriber.subscribe(channel.siteChannelID, expired)
                        }
                        currentSubscriptions = currentSubscriptions + topic
                    }
                }
                // todo unsubscribe to entries in currentSubscriptions without active targets or at least ignore

            }
        }
    }

}