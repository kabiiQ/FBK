package moe.kabii.trackers.videos.kick.watcher

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeout
import moe.kabii.LOG
import moe.kabii.data.relational.streams.DBStreams
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.instances.DiscordInstances
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.videos.kick.parser.KickParser
import moe.kabii.trackers.videos.kick.parser.KickStreamInfo
import moe.kabii.util.extensions.RequiresExposedContext
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
            }.associate { chan ->
                chan.siteChannelID.toLong() to chan.id
            }
        }

        // See TwitchChecker for more comments on this - identical process associating id with data
        val ids = tracked.keys
        val channelData = KickParser.getChannels(ids)

        // call updateChannel, merging retrieved channel info with db
        channelData.mapNotNull { (id, channel) ->
            when (channel) {
                is Err -> {
                    if (channel.value == TrackerErr.NotFound) LOG.warn("Kick channel $id returned not found error!")
                    else LOG.warn("Error contacting Kick: channel $id")
                    null
                }
                is Ok -> {
                    taskScope.launch {
                        propagateTransaction {
                            try {
                                val trackedChannel = TrackedStreams.StreamChannel.findById(tracked.getValue(id))!!
                                val filteredTargets = getActiveTargets(trackedChannel)
                                if (filteredTargets != null) {
                                    updateChannel(trackedChannel, channel.value, filteredTargets)
                                } // else channel has been untracked entirely
                            } catch (e: Exception) {
                                LOG.warn("Error updating Kick channel: $id")
                                LOG.debug(e.stackTraceString)
                            }
                        }
                    }
                }
            }
        }.joinAll()
    }

    @RequiresExposedContext
    suspend fun updateChannel(db: TrackedStreams.StreamChannel, kick: KickStreamInfo, targets: List<TrackedTarget>) {
        // Lock channel to prevent poller overlap with webhooks
        val lock = db.updateLock()
        if(!lock.tryLock()) {
            LOG.debug("Skipping Kick update for ${db.lastKnownUsername} - already in use")
            return
        }

        try {
            val dbStream = propagateTransaction {
                DBStreams.LiveStreamEvent.getKickStreamFor(db)
            }
            if (kick.live) {

                if (dbStream != null) {

                    // stream is already known, update stats
                    propagateTransaction {
                        dbStream.updateViewers(kick.viewers)
                        if (kick.title != dbStream.lastTitle || kick.category.name != dbStream.lastGame) {
                            dbStream.lastTitle = kick.title
                            dbStream.lastGame = kick.category.name
                        }

                        val (_, existingEvent) = eventManager.targets(db)
                        existingEvent
                            .forEach { event ->
                                eventManager.updateLiveEvent(event, kick.title)
                            }
                    }

                } else {
                    // new stream has started
                    streamStart(db, kick)
                }

                // stream is live, edit or post a notification in each target channel
                targets
                    .filter { target -> DBStreams.Notification.getForTarget(target).empty() }
                    .forEach { target ->
                        try {
                            createLiveNotification(db, kick, target)
                        } catch(e: Exception) {
                            LOG.warn("Error while sending Kick notification for channel: ${kick.slug} :: ${e.message}")
                            LOG.debug(e.stackTraceString)
                        }
                    }

            } else {

                // stream is not live, check if there are any existing notifications to remove
                if (dbStream != null) {
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
        } finally {
            if(lock.isLocked) lock.unlock()
        }
    }
}