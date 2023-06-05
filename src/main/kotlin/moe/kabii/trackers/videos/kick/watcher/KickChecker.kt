package moe.kabii.trackers.videos.kick.watcher

import kotlinx.coroutines.time.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.videos.kick.api.KickChannel
import moe.kabii.trackers.videos.kick.api.KickParser
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class KickChecker(instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : Runnable, KickNotifier(instances) {

    override fun run() {
        applicationLoop {
            // poller to check tracked kick streams
            val start = Instant.now()

            try {
                // get all tracked kick channels
                val tracked = propagateTransaction {
                    TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.KICK
                    }
                }

                tracked.forEach { channel ->
                    // call each kick stream sequentially - unknown rate limits
                    try {
                        val kickChannel = KickParser.getChannel(channel.siteChannelID)
                        if(kickChannel == null) {
                            LOG.warn("Error getting Kick channel: ${channel.siteChannelID}")
                            return@forEach
                        }

                        val filteredTargets = getActiveTargets(channel)
                        if(filteredTargets != null) {
                            updateChannel(channel, kickChannel, filteredTargets)
                        }
                    } catch(e: Exception) {
                        LOG.warn("Error updating Kick channel: $channel")
                    }
                }
            } catch(e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    suspend fun updateChannel(db: TrackedStreams.StreamChannel, kick: KickChannel, targets: List<TrackedTarget>) {
        TODO()
    }
}