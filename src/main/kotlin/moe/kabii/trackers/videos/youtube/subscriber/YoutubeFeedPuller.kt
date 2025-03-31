package moe.kabii.trackers.videos.youtube.subscriber

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class YoutubeFeedPuller(val cooldown: ServiceRequestCooldownSpec) : Runnable {
    override fun run() {
        applicationLoop {
            val start = Instant.now()
            propagateTransaction {
                TrackedStreams.StreamChannel.getActive {
                    TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.YOUTUBE
                }.forEach { feed ->
                    val channel = feed.siteChannelID
                    LOG.debug("Manually pulling feed updates for $channel")
                    YoutubeVideoIntake.intakeAsync(channel)
                    delay(cooldown.callDelay)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldown.minimumRepeatTime - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }
}