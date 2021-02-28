package moe.kabii.discord.trackers.videos.youtube.subscriber

import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.ServiceRequestCooldownSpec
import moe.kabii.structure.extensions.applicationLoop
import moe.kabii.structure.extensions.propagateTransaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
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
                    YoutubeVideoIntake.intakeExisting(channel)
                    delay(cooldown.callDelay)
                }
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldown.minimumRepeatTime - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }
}