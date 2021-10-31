package moe.kabii.trackers.videos.youtube.subscriber

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.WebSubSubscription
import moe.kabii.discord.util.Metadata
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.youtube.watcher.YoutubeChecker
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant

class YoutubeSubscriptionManager(discord: GatewayDiscordClient, val cooldowns: ServiceRequestCooldownSpec) : Runnable, StreamWatcher(discord) {

    lateinit var checker: YoutubeChecker
    val subscriber = YoutubeFeedSubscriber()
    private val listener = YoutubeFeedListener(this)

    var currentSubscriptions = setOf<String>()

    companion object {
        val maxSubscriptionInterval = Duration.standardDays(2)
    }

    override fun run() {
        // start callback server
        if(!Metadata.host) return
        listener.server.start()

        currentSubscriptions = transaction {
            WebSubSubscription
                .getCurrent(TrackedStreams.DBSite.YOUTUBE, maxSubscriptionInterval)
                .map { feed ->
                    feed.webSubChannel.siteChannelID
                }.toSet()
        }

        applicationLoop {
            newSuspendedTransaction {
                try {
                    // subscribe to updates for all channels with active targets
                    val ytChannels = TrackedStreams.StreamChannel.getActive {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE
                    }
                    ytChannels.forEach { channel ->
                        val subscription = channel.subscription.firstOrNull()

                        // only make actual call to google if last call is old - but still maintain current list in memory
                        if (subscription == null) {
                            LOG.info("new subscription: ${channel.siteChannelID}")
                            val callTopic = subscriber.subscribe(channel.siteChannelID)
                            if (callTopic != null) {
                                WebSubSubscription.new {
                                    this.webSubChannel = channel
                                    this.lastSubscription = DateTime.now()
                                }
                            }
                        } else {
                            val lastSub = subscription.lastSubscription
                            val expired = !currentSubscriptions.contains(channel.siteChannelID) || Duration(lastSub, Instant.now()) >= maxSubscriptionInterval
                            if (expired) {
                                if (subscriber.subscribe(channel.siteChannelID) != null) {
                                    subscription.lastSubscription = DateTime.now()
                                }
                            }
                        }
                        currentSubscriptions = currentSubscriptions + channel.siteChannelID
                    }
                } catch (e: Exception) {
                    LOG.error("Uncaught error in YoutubeSubscriptionManager: ${e.message}")
                    LOG.warn(e.stackTraceString)
                }
            }

            // no un-necessary calls are made here, we can run this on a fairly low interval
            delay(cooldowns.minimumRepeatTime)
        }
    }
}