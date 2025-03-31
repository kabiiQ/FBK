package moe.kabii.trackers.videos.youtube.watcher

import kotlinx.coroutines.*
import moe.kabii.LOG
import moe.kabii.data.relational.streams.youtube.*
import moe.kabii.data.temporary.Locks
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.trackers.ServiceRequestCooldownSpec
import moe.kabii.trackers.TrackerErr
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.trackers.videos.youtube.YoutubeVideoInfo
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.util.extensions.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

sealed class YoutubeCall(val video: YoutubeVideo, val code: String) {
    class Live(val live: YoutubeLiveEvent) : YoutubeCall(live.ytVideo, "l")
    class Scheduled(val scheduled: YoutubeScheduledEvent, code: String = "s") : YoutubeCall(scheduled.ytVideo, code)
    class New(val new: YoutubeVideo) : YoutubeCall(new, "n")
}

class YoutubeChecker(subscriptions: YoutubeSubscriptionManager, cooldowns: ServiceRequestCooldownSpec): Runnable, YoutubeNotifier(subscriptions) {
    private val idChunk = 20 // can include up to 20 video IDs in 1 request
    private val cleanInterval = 180 // only clean db approx every 1.5 hours
    private val repeatTimeMillis = cooldowns.minimumRepeatTime
    private val tickDelay = cooldowns.callDelay

    private val ytScope = CoroutineScope(DiscordTaskPool.streamThreads + CoroutineName("YouTube-Intake") + SupervisorJob())

    private var nextCall = Instant.now()
    private var tickId = 0

    override fun run() {
        applicationLoop {
            val start = Instant.now()
            try {
                ytTick(start)
            } catch(e: Exception) {
                LOG.warn("Error in YoutubeChecker#ytTick: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
            val runDuration = Duration.between(start, Instant.now())
            val delay = repeatTimeMillis - runDuration.toMillis() - 250
            delay(max(delay, 0L))
        }
    }

    suspend fun ytTick(start: Instant = Instant.now()) {
        // perform yt tick only if repeatTime has elapsed since last call (may be called sooner by yt push event)
        this.nextCall = Instant.now().plusMillis(repeatTimeMillis)
        LOG.debug("start: $start :: nextCall : $nextCall")
        // youtube api has daily quota limits - we only hit /videos/ API and thus can chunk all of our calls
        // gather all youtube IDs that need to be checked in the API

        // create lookup map to associate video id with the original 'type' as it will be lost when passed to the youtube API
        // <video id, target list>
        val targetLookup = mutableMapOf<String, YoutubeCall>()

        kotlinx.coroutines.time.withTimeout(Duration.ofSeconds(12)) {
            propagateTransaction {
                val currentTime = DateTime.now()

                // 1: collect all videos we have as 'currently live' - don't recheck within 3 minutes-ish
                val reCheck = currentTime.minusMinutes(3)
                val dbLiveVideos = YoutubeLiveEvent.wrapRows(
                    YoutubeLiveEvents
                        .innerJoin(YoutubeVideos)
                        .select {
                            YoutubeVideos.lastAPICall lessEq reCheck
                        }
                )
                dbLiveVideos.forEach { live ->
                    val callReason = YoutubeCall.Live(live)
                    targetLookup[callReason.video.videoId] = callReason
                }

                // 2: collect all 'scheduled' videos with 'expire' update timer due
                val dbScheduledVideos = YoutubeScheduledEvent.wrapRows(
                    YoutubeScheduledEvents.select {
                        YoutubeScheduledEvents.dataExpiration lessEq currentTime
                    }
                )
                dbScheduledVideos.forEach { scheduled ->
                    val callReason = YoutubeCall.Scheduled(scheduled)
                    targetLookup[callReason.video.videoId] = callReason
                }

                // 3: collect all videos that are 'new' and we have no data on
                val dbNewVideos = YoutubeVideo.find {
                    YoutubeVideos.lastAPICall eq null
                }
                dbNewVideos.forEach { new ->
                    val callReason = YoutubeCall.New(new)
                    targetLookup[callReason.video.videoId] = callReason
                }

                // check if we have 'wasted space' - an API call that will be made with less than 20 videos
                // check on random scheduled videos using this otherwise wasted space
                val remainder = targetLookup.size % idChunk
                if(remainder != 0) {
                    val backfill = YoutubeScheduledEvent.wrapRows(
                        YoutubeScheduledEvents.selectAll()
                            .orderBy(Random())
                            .limit(idChunk - remainder)
                    ).toList()
                    backfill.forEach { event ->
                        val callReason = YoutubeCall.Scheduled(event, "r")
                        targetLookup[callReason.video.videoId] = callReason
                    }
                }
            }
        }

        // main IO call, process as we go
        val expected = targetLookup
            .map { (id, call) -> "${call.code}:$id" }
            .joinToString(",")
        LOG.debug("yt expected calls: $expected")
        ytScope.launch {
            try {
                kotlinx.coroutines.time.withTimeout(Duration.ofSeconds(
                    (((targetLookup.size / idChunk) + 1) * 20).toLong()
                )) {
                    var first = true
                    targetLookup.keys
                        .chunked(20)
                        .flatMap { chunk ->
                            LOG.debug("yt api call: $chunk")
                            if(first) first = false
                            else Thread.sleep(500L)
                            YoutubeParser.getVideos(chunk).entries
                        }.forEach { (videoId, ytVideo) ->

                            // 'lock' this video from being updated concurrently
                            val lock = Locks.YoutubeVideo[videoId]
                            if(!lock.tryLock()) {
                                LOG.debug("Skipping YouTube video update for ${videoId} - already in use")
                                return@forEach
                            }

                            try {
                                // launch coroutine for each video to be processed
                                discordTask(30_000L) {
                                    try {
                                        val callReason = targetLookup.getValue(videoId)

                                        val ytVideoInfo = when(ytVideo) {
                                            is Ok -> ytVideo.value
                                            is Err -> {
                                                LOG.error("YouTube video not processing: $videoId :: ${ytVideo.value}")
                                                when (ytVideo.value) {
                                                    // do not process video if this was an IO issue on our end
                                                    is TrackerErr.Network -> return@discordTask
                                                    is TrackerErr.NotFound -> null
                                                }
                                            }
                                        }

                                        propagateTransaction {
                                            if (ytVideoInfo != null) {
                                                with(callReason.video) {
                                                    lastAPICall = DateTime.now()
                                                    memberLimited = ytVideoInfo.memberLimited
                                                    lastTitle = ytVideoInfo.title
                                                    ytChannel.lastKnownUsername = ytVideoInfo.channel.name
                                                }
                                            }

                                            when (callReason) {
                                                is YoutubeCall.Live -> currentLiveCheck(callReason, ytVideoInfo)
                                                is YoutubeCall.Scheduled -> upcomingCheck(callReason, ytVideoInfo)
                                                is YoutubeCall.New -> newVideoCheck(callReason, ytVideoInfo)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        LOG.warn("Error processing YouTube video: $videoId :: ${e.message}")
                                        LOG.debug(e.stackTraceString)
                                    }
                                }

                            } finally {
                                if(lock.isLocked) lock.unlock()
                            }
                        }

                    LOG.debug("yt exit")

                    // clean up videos db
                    if(tickId == 10) {
                        LOG.debug("Executing YouTube DB cleanup")
                        propagateTransaction {
                             // previously handled videos - 1 month old
                            val old = DateTime.now().minusWeeks(4)
                            YoutubeVideos.deleteWhere {
                                YoutubeVideos.lastAPICall lessEq old and
                                        // don't delete 'long term' videos that may be used for ytchat
                                        (YoutubeVideos.scheduledEvent eq null)
                            }
                            // streams which never went live (with 1 day of leniency)
                            val overdue = DateTime.now().minusDays(1)
                            YoutubeScheduledEvents.deleteWhere {
                                YoutubeScheduledEvents.scheduledStart less overdue
                            }
                            /* strange streams which youtube sometimes creates - it does not seem possible to distinguish these from brand
                            new stream entries. They are 'upcoming' streams with no scheduled start time
                             */
                            YoutubeVideos.deleteWhere {
                                YoutubeVideos.lastAPICall eq null and
                                        (YoutubeVideos.apiAttempts greater 10)
                            }

                            Locks.YoutubeVideo.purgeLocks()
                        }
                    }
                }
            } catch(time: TimeoutCancellationException) {
                LOG.warn("YoutubeChecker routine: timeout reached")
            } catch(e: Exception) {
                LOG.info("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
        tickId = (tickId + 1) mod cleanInterval
    }

    @RequiresExposedContext
    private suspend fun currentLiveCheck(call: YoutubeCall.Live, ytVideo: YoutubeVideoInfo?) {
        val dbLive = call.live

        // if this function is called, video is marked as live in DB. check current state
        if(ytVideo?.live == true) {
            // stream is still live, update information
            val viewers = ytVideo.liveInfo?.concurrent
            if(viewers != null) {
                dbLive.updateViewers(viewers)
            } // else case seems to happen with membership streams that weren't initially private ? or similar cases

            // iterate all targets and make sure they have a notification - if a stream is tracked in a different server/channel while live, it would not be posted
            filteredTargets(call.video.ytChannel, ytVideo, dbLive::shouldPostLiveNotice).forEach { target ->
                // verify target already has a notification
                if(YoutubeNotification.getExisting(target, call.video).empty()) {
                    try {
                        createLiveNotification(ytVideo, call.video, target, new = false)
                    } catch(e: Exception) {
                        // catch and consume all exceptions here - if one target fails, we don't want this to affect the other targets in potentially different discord servers
                        LOG.warn("Error while creating live notification for channel: ${ytVideo.channel} :: ${e.message}")
                        LOG.debug(e.stackTraceString)
                    }
                }
            }

            // Get any Discord scheduled events that exist for this video
            val (noEvent, existingEvent) = eventManager.targets(dbLive.ytVideo.ytChannel, call.video)
            // update all targets that require (missing) scheduled event to be created
            noEvent
                .forEach { target ->
                    eventManager.scheduleEvent(
                        target, ytVideo.url, ytVideo.title, ytVideo.description, dbLive.ytVideo, ytVideo.thumbnail
                    )
                }

            // check targets that already have scheduled event for end times that are approaching
            existingEvent
                .forEach { event ->
                    eventManager.updateLiveEvent(event, ytVideo.title)
                }

        } else {
            // stream has ended (live = false or video deleted)
            streamEnd(ytVideo, dbLive)
        }
    }

    @RequiresExposedContext
    private suspend fun upcomingCheck(call: YoutubeCall.Scheduled, ytVideo: YoutubeVideoInfo?) {
        val dbEvent = call.scheduled
        when {
            // scheduled video is not accessible for us
            // leave 'video' in db in case it is re-published, we don't need to notify again
            ytVideo == null -> dbEvent.delete()
            ytVideo.live -> {
                // scheduled stream has started
                streamStart(ytVideo, call.video)
                dbEvent.delete()
            }
            ytVideo.upcoming -> {
                // event still exists and is not live yet
                val scheduled = ytVideo.liveInfo?.scheduledStart
                if(scheduled != null) {
                    propagateTransaction {
                        dbEvent.scheduledStart = scheduled.jodaDateTime

                        // set next update time to 1/2 time until stream start
                        val untilStart = Duration.between(Instant.now(), scheduled)
                        val halfway = max(untilStart.toMillis() / 2, 0)

                        val updateInterval = when {
                            dbEvent.apiCalls <= 2 -> {
                                // special condition: keep polling newly scheduled streams as certain people tend to schedule streams incorrectly and then fix
                                // provides roughly 15 minutes to 'fix' stream while still not constantly re-polling fake streams/chat rooms that are far in the future
                                min(halfway, Duration.ofMinutes(5).toMillis())
                            }
                            untilStart < Duration.ofMinutes(-20) -> {
                                // special condition: stream did not start on time and is being rechecked every minute
                                // after 20 minutes overdue, begin penalizing re-polling of stream
                                Duration.ofMinutes(2).toMillis()
                            }
                            untilStart < Duration.ofHours(-1) -> {
                                Duration.ofMinutes(4).toMillis()
                            }
                            untilStart < Duration.ofHours(-2) -> {
                                // stream is 2 hours overdue, unlikely to go live at this point
                                Duration.ofMinutes(6).toMillis()
                            }
                            else -> halfway
                        }

                        val nextUpdate = DateTime.now().plus(updateInterval)
                        dbEvent.dataExpiration = nextUpdate
                        dbEvent.apiCalls += 1

                        // send out 'upcoming' notifications
                        streamUpcoming(dbEvent, ytVideo, scheduled)
                    }
                } else {
                    LOG.warn("YouTube returned SCHEDULED stream with no start time: ${ytVideo.id}")
                }
            }
            else -> {
                // video exists, never went live ?
                dbEvent.delete()
            }
        }
    }

    private suspend fun newVideoCheck(call: YoutubeCall.New, ytVideo: YoutubeVideoInfo?) {
        if(ytVideo == null) {
            // if we don't have information on this video, and youtube provides no information, remove it.
            call.new.delete()
            return
        }
        val dbVideo = call.video
        when {
            ytVideo.upcoming -> {
                val scheduled = ytVideo.liveInfo?.scheduledStart
                if(scheduled == null) {
                    dbVideo.apiAttempts += 1
                    LOG.debug("YouTube provided UPCOMING video with no start time: ${ytVideo.id} :: check ${dbVideo.apiAttempts}")
                    // yt did not return the relevant information for this "upcoming" stream yet
                    // this stream frame should continue to be processed as a new video
                    dbVideo.lastAPICall = null
                    return
                }
                // assign video 'scheduled' status
                val dbScheduled = propagateTransaction {
                    val dbScheduled = YoutubeScheduledEvent.getScheduled(dbVideo)
                        ?: YoutubeScheduledEvent.new {
                            this.ytVideo = dbVideo
                            this.scheduledStart = scheduled.jodaDateTime
                            this.dataExpiration = DateTime.now() // todo move calculation to function ?
                        }
                    dbVideo.scheduledEvent = dbScheduled
                    dbScheduled
                }

                // send 'upcoming' and/or 'creation' messages to appropriate targets
                streamUpcoming(dbScheduled, ytVideo, scheduled)
                streamCreated(dbVideo, ytVideo)
            }
            ytVideo.live -> {
                streamStart(ytVideo, dbVideo)
            }
            else -> {
                // regular video upload
                videoUploaded(dbVideo, ytVideo)
            }
        }
    }
}