package moe.kabii.trackers.videos.kick.webhook

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.kick.KickEventSubscription
import moe.kabii.data.relational.streams.kick.KickEventSubscriptions
import moe.kabii.instances.DiscordInstances
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.kick.parser.KickParser
import moe.kabii.trackers.videos.kick.watcher.KickChecker
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import org.jetbrains.exposed.sql.transactions.transaction

class KickSubscriptionManager(instances: DiscordInstances, checker: KickChecker, val cooldowns: ServiceRequestCooldownSpec): Runnable, StreamWatcher(instances) {

    private val listener = KickWebhookListener(this, checker)

    private var currentSubscriptions = setOf<Int>()

    override fun run() {
        // start callback server
        listener.server.start()

        currentSubscriptions = transaction {
            KickEventSubscription.all().map { sub -> sub.id.value }
        }.toSet()

        applicationLoop {
            propagateTransaction {
                try {
                    // check all current subscriptions
                    currentSubscriptions.forEach { subscription ->
                        // find subscriptions that no longer have an active streamchannel
                        val dbSub = KickEventSubscription[subscription]
                        if(dbSub.kickChannel == null) {
                            LOG.info("Unsubscribing from Kick webhook: ${dbSub.subscriptionId}")
                            KickParser.Webhook.deleteSubscription(dbSub.subscriptionId)
                            currentSubscriptions = currentSubscriptions - dbSub.id.value
                            dbSub.delete()
                        }
                    }

                    // get all streams which should have an active subscription
                    val kickChannels = TrackedStreams.StreamChannel.getActive {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.KICK
                    }

                    kickChannels.forEach { channel ->
                        val subscription = KickEventSubscription.getExisting(channel, KickEventSubscriptions.Type.STREAM_UPDATED).firstOrNull()

                        if(subscription == null) {
                            LOG.info("New Kick webhook: ${channel.siteChannelID}")
                            val sub = KickParser.Webhook.createSubscription(KickEventSubscriptions.Type.STREAM_UPDATED, channel.siteChannelID.toLong())
                            if(sub is Ok) {
                                val dbSub = KickEventSubscription.new {
                                    this.kickChannel = channel
                                    this.eventType = KickEventSubscriptions.Type.STREAM_UPDATED
                                    this.subscriptionId = sub.value.subscriptionId
                                }
                                currentSubscriptions = currentSubscriptions + dbSub.id.value
                            } else {
                                LOG.warn("Error creating creating Kick webhook subscription for ${channel.siteChannelID}")
                            }
                        }
                    }
                } catch(e: Exception) {
                    LOG.error("Uncaught error in KickSubscriptionManager: ${e.message}")
                    LOG.warn(e.stackTraceString)
                }
            }
            delay(cooldowns.minimumRepeatTime)
        }
    }

}