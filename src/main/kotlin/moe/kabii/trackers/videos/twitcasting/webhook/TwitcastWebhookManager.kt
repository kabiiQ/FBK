package moe.kabii.trackers.videos.twitcasting.webhook

import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.trackers.videos.twitcasting.json.TwitcastingWebhook
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.time.Duration

object TwitcastWebhookManager : Runnable {

    override fun run() {
        applicationLoop {
            try {
                validateAll()
            } catch(e: Exception) {
                LOG.warn("Error in TwitcastWebhookManager->validation: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            delay(Duration.ofHours(12))
        }
    }

    private var activeWebhooks = mutableListOf<String>()

    private suspend fun validateAll() {
        propagateTransaction {

            // get all active webhooks
            val webhooks = mutableListOf<TwitcastingWebhook>()
            var offset = 0
            do {
                val page = TwitcastingParser.getWebhooks(offset)
                webhooks.addAll(page.webhooks.distinctBy(TwitcastingWebhook::userId))
                offset += 50
            } while(offset < page.totalCount)

            // get all twitcasting streams with a target - all should have a webhook registered
            val (registered, nonregistered) = TrackedStreams.StreamChannel.getActive {
                TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.TWITCASTING
            }.partition { channel ->
                // true if webhook exists
                webhooks.any { webhook ->
                    webhook.userId == channel.siteChannelID
                }
            }
            activeWebhooks = registered.map(TrackedStreams.StreamChannel::siteChannelID).toMutableList()

            // register any tracked streams without a webhook
            nonregistered.forEach { channel -> register(channel.siteChannelID) }

            webhooks.filter { webhook ->
                registered.none { channel ->
                    channel.siteChannelID == webhook.userId
                }
            }.forEach { extraHook -> unregister(extraHook.userId) }
        }
    }

    suspend fun register(userId: String) {
        if(activeWebhooks.contains(userId)) return
        val added = TwitcastingParser.registerWebhook(userId)
        if(added) {
            activeWebhooks.add(userId)
        }
    }

    suspend fun unregister(userId: String) {
        val removed = TwitcastingParser.unregisterWebhook(userId)
        if(removed) {
            activeWebhooks.remove(userId)
        }
    }
}