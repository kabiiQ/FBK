package moe.kabii.trackers.videos.kick.watcher

import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeout
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitch.DBStreams
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

    private val callDelay = Duration.ofMillis(cooldowns.callDelay)

    override fun run() {
        applicationLoop {
            // poller to check tracked kick streams
            val start = Instant.now()

            try {
                withTimeout(Duration.ofMinutes(6)) {
                    checkAll()
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

    private suspend fun checkAll() {
        // get all tracked kick channels
        val tracked = propagateTransaction {
            TrackedStreams.StreamChannel.find {
                TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.KICK
            }.toList()
        }

        tracked.forEach { channel ->
            // call each kick stream sequentially - unknown rate limits
            try {
                delay(callDelay)
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
                LOG.debug(e.stackTraceString)
            }
        }
    }

    suspend fun updateChannel(db: TrackedStreams.StreamChannel, kick: KickChannel, targets: List<TrackedTarget>) {
        val stream = kick.livestream
        val dbStream = propagateTransaction {
            DBStreams.LiveStreamEvent.getKickStreamFor(db)
        }
        if(stream?.live == true) {

            // stream is live, edit or post a notification in each target channel
            if(dbStream != null) {

                // stream is already known, update stats
                propagateTransaction {
                    dbStream.updateViewers(stream.viewers)
                    if(stream.title != dbStream.lastTitle || stream.categories != dbStream.lastGame) {
                        dbStream.lastTitle = stream.title
                        dbStream.lastGame = stream.categories
                    }

                    val (_, existingEvent) = eventManager.targets(db)
                    existingEvent
                        .forEach { event ->
                            eventManager.updateLiveEvent(event, stream.title)
                        }
                }

            } else {
                // new stream has started
                streamStart(db, kick, targets)
            }

        } else {

            // stream is not live, check if there are any existing notifications to remove
            if(dbStream != null) {

                // no longer live but we have stream history. edit/remove any notifications and delete history
                try {
                    streamEnd(dbStream, kick)
                } finally {
                    propagateTransaction {
                        dbStream.delete()
                    }
                }

            } // else stream is not live and there are no notifications
        }
    }
}