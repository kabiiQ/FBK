package moe.kabii.trackers.videos.twitch.webhook

import discord4j.core.GatewayDiscordClient
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.WebSubSubscription
import moe.kabii.discord.util.Metadata
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant

class TwitchSubscriptionManager(discord: GatewayDiscordClient, checker: TwitchChecker, val cooldowns: ServiceRequestCooldownSpec) : Runnable, StreamWatcher(discord) {

    val subscriber = TwitchFeedSubscriber()
    private val listener = TwitchWebhookListener(this, checker)

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
                .getCurrent(TrackedStreams.DBSite.TWITCH, maxSubscriptionInterval)
                .map { feed ->
                    feed.webSubChannel.siteChannelID
                }.toSet()
        }

        applicationLoop {
            newSuspendedTransaction {
                try {
                    val twitchChannels = TrackedStreams.StreamChannel.getActive {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCH
                    }
                    twitchChannels.forEach { channel ->
                        val subscription = channel.subscription.firstOrNull()

                        if(subscription == null) {
                            LOG.info("New Twitch webhook: ${channel.siteChannelID}")
                            val callTopic = subscriber.subscribe(channel.siteChannelID)
                            if(callTopic != null) {
                                WebSubSubscription.new {
                                    this.webSubChannel = channel
                                    this.lastSubscription = DateTime.now()
                                }
                            }
                        } else {
                            val lastSub = subscription.lastSubscription
                            val expired = !currentSubscriptions.contains(channel.siteChannelID) || Duration(lastSub, Instant.now()) >= maxSubscriptionInterval
                            if(expired) {
                                if(subscriber.subscribe(channel.siteChannelID) != null) {
                                    subscription.lastSubscription = DateTime.now()
                                }
                            }
                        }
                        currentSubscriptions = currentSubscriptions + channel.siteChannelID
                    }

                } catch(e: Exception) {
                    LOG.error("Uncaught error in TwitchSubscriptionManager: ${e.message}")
                    LOG.warn(e.stackTraceString)
                }
            }
            delay(cooldowns.minimumRepeatTime)
        }
    }

}