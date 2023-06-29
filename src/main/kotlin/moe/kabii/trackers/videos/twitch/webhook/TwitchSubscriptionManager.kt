package moe.kabii.trackers.videos.twitch.webhook

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscription
import moe.kabii.data.relational.streams.twitch.TwitchEventSubscriptions
import moe.kabii.discord.util.MetaData
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.twitch.parser.TwitchParser
import moe.kabii.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.transaction

class TwitchSubscriptionManager(instances: DiscordInstances, checker: TwitchChecker, val cooldowns: ServiceRequestCooldownSpec) : Runnable, StreamWatcher(instances) {

    private val listener = TwitchWebhookListener(this, checker)

    var currentSubscriptions = setOf<Int>()

    internal fun subscriptionComplete(sub: TwitchEventSubscription) {
        currentSubscriptions = currentSubscriptions + sub.id.value
    }

    internal fun subscriptionRevoked(oldId: Int) {
        currentSubscriptions = currentSubscriptions - oldId
    }

    override fun run() {
        // start callback server
        if(!MetaData.host) return
        listener.server.start()

        currentSubscriptions = transaction {
            TwitchEventSubscription.all().map { sub -> sub.id.value }
        }.toSet()

        applicationLoop {
            propagateTransaction {
                try {
                    // check all current subscriptions
                    currentSubscriptions.forEach { subscription ->
                        // find subscriptions that no longer have an active streamchannel
                        val dbSub = TwitchEventSubscription[subscription]
                        if(dbSub.twitchChannel == null) {
                            LOG.info("Unsubscribing from Twitch webhook: ${dbSub.subscriptionId}")
                            TwitchParser.EventSub.deleteSubscription(dbSub.subscriptionId)
                            subscriptionRevoked(dbSub.id.value)
                            dbSub.delete()
                        }
                    }

                    // get all streams which should have an active subscription
                    val twitchChannels = TrackedStreams.StreamChannel.getActive {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCH
                    }

                    twitchChannels.forEach { channel ->
                        val subscription = TwitchEventSubscription.getExisting(channel, TwitchEventSubscriptions.Type.START_STREAM).firstOrNull()

                        if(subscription == null) {
                            LOG.info("New Twitch webhook: ${channel.siteChannelID}")
                            TwitchParser.EventSub.createSubscription(TwitchEventSubscriptions.Type.START_STREAM, channel.siteChannelID.toLong())
                        }
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