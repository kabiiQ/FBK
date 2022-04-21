package moe.kabii.trackers.videos.spaces.watcher

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import moe.kabii.instances.DiscordInstances
import moe.kabii.LOG
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.spaces.TwitterSpaces
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.discord.trackers.twitter.json.TwitterSpace
import moe.kabii.discord.trackers.twitter.json.TwitterSpaceState
import moe.kabii.discord.trackers.videos.spaces.watcher.SpaceNotifier
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.twitter.TwitterParser
import moe.kabii.trackers.twitter.TwitterRateLimitReachedException
import moe.kabii.trackers.twitter.json.TwitterTweet
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.applicationLoop
import moe.kabii.util.extensions.propagateTransaction
import moe.kabii.util.extensions.stackTraceString
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class SpaceChecker(instances: DiscordInstances, val cooldowns: ServiceRequestCooldownSpec) : Runnable, SpaceNotifier(instances) {

    private val spacePattern = Regex("twitter.com/i/spaces/([a-zA-Z0-9]{13})")
    private val spaceContext = CoroutineScope(DiscordTaskPool.streamThreads + CoroutineName("TwitterChecker-SpaceIntake") + SupervisorJob())

    private suspend fun <R> wrapAndTry(query: () -> R): R? {
        return try {
            query()
        } catch(rate: TwitterRateLimitReachedException) {
            val reset = rate.reset
            LOG.warn("Twitter rate limit reached: sleeping ${reset.seconds} seconds")
            delay(reset)
            wrapAndTry(query)
        } catch(e: Exception) {
            LOG.error("Error getting chunk of Spaces: ${e.message}")
            return null
        }
    }

    override fun run() {
        applicationLoop {
            val start = Instant.now()

            propagateTransaction {

                try {
                    val liveSpaces = TwitterSpaces.Space.all()

                    // check all trackers users that are not currently known to be live
                    val checkUsers = TrackedStreams.StreamChannel.find {
                        TrackedStreams.StreamChannels.site eq TrackedStreams.DBSite.SPACES
                    }.filter { channel ->
                        liveSpaces.find { live -> live.channel == channel } == null
                    }

                    var first = true
                    checkUsers.chunked(100).forEach { userChunk ->
                        if(!first) {
                            delay(Duration.ofMillis(cooldowns.callDelay))
                        }  else first = false

                        wrapAndTry {
                            TwitterParser
                                .getSpacesByCreators(userChunk.map { chan -> chan.siteChannelID })
                        }?.forEach { liveSpace ->
                            val channel = userChunk.find { user -> user.siteChannelID == liveSpace._creatorId }
                            updateSpace(checkNotNull(channel), liveSpace)
                        }
                    }

                    // then, check all 'live' spaces to verify they are still live
                    first = true
                    liveSpaces.chunked(100).forEach { spaceChunk ->
                        if(!first) {
                            delay(Duration.ofMillis(cooldowns.callDelay))
                        } else first = false

                        wrapAndTry {
                            TwitterParser
                                .getSpaces(spaceChunk.map { space -> space.spaceId })
                        }?.forEach { checkSpace ->
                            val dbSpace = spaceChunk.find { space -> space.spaceId == checkSpace.id }
                            updateSpace(checkNotNull(dbSpace).channel, checkSpace)
                        }
                    }
                } catch(e: Exception) {
                    LOG.warn("Exception in SpaceChecker : ${e.message}")
                    LOG.debug(e.stackTraceString)
                }
            }

            val runDuration = Duration.between(start, Instant.now())
            val delay = cooldowns.minimumRepeatTime - runDuration.toMillis()
            delay(Duration.ofMillis(max(delay, 0L)))
        }
    }

    @WithinExposedContext
    suspend fun updateSpace(dbChannel: TrackedStreams.StreamChannel, space: TwitterSpace) {

        when(space.state) {
            TwitterSpaceState.LIVE -> {
                val targets = getActiveTargets(dbChannel) ?: return
                updateLiveSpace(dbChannel, space, targets)
            }
            TwitterSpaceState.ENDED -> updateEndedSpace(dbChannel, space)
            TwitterSpaceState.SCHEDULED -> return // scheduled spaces not supported for now, just keep checking until live or cancelled
        }
    }

    @WithinExposedContext
    suspend fun intakeSpaceFromTweet(tweet: TwitterTweet) {
        val spaceMatch = tweet.entities?.urls?.firstNotNullOfOrNull { urls ->
            urls.expanded?.run(spacePattern::find)?.groups?.get(1)?.value
        }
        spaceMatch ?: return

        // if tweet contains space url, check if already known
        val existing = TwitterSpaces.Space.find { TwitterSpaces.Spaces.spaceId eq spaceMatch }.firstOrNull()
        if(existing != null) return

        spaceContext.launch {
            try {

                propagateTransaction {
                    val liveSpace = TwitterParser.getSpace(spaceMatch) ?: return@propagateTransaction
                    // check if this is even a tracked user's space
                    val dbChannel = TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.SPACES, liveSpace._creatorId) ?: return@propagateTransaction
                    updateSpace(dbChannel, liveSpace)
                }

            } catch(e: Exception) {
                LOG.warn("Error retrieving Twitter Space: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}