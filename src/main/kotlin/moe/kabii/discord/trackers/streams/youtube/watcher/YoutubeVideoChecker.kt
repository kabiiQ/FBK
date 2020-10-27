package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.delay
import moe.kabii.LOG
import moe.kabii.data.relational.streams.DBYoutubeStreams
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.trackers.streams.youtube.YoutubeParser
import moe.kabii.discord.trackers.streams.youtube.YoutubeVideoInfo
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.loop
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.util.DurationFormatter
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class YoutubeVideoChecker(discord: GatewayDiscordClient) : Runnable, YoutubeWatcher(discord) {
    companion object {
        private val inactiveColor = Color.of(8847360)
    }

    override fun run() {
        loop {
            val start = Instant.now()

            // check known live streams, can group up to 50 video IDs in an API call
            try {
                newSuspendedTransaction {
                    DBYoutubeStreams.YoutubeStream.all()
                        .asSequence()
                        .shuffled()
                        .chunked(50)
                        .flatMap { chunk ->
                            // chunk, call API, and match api response back to to database object
                            val chunkIds = chunk.map(DBYoutubeStreams.YoutubeStream::youtubeVideoId)
                            val videos = YoutubeParser.getVideos(chunkIds)
                            chunk.associateWith { stream ->
                                videos.getValue(stream.youtubeVideoId)
                            }.entries
                        }.forEach { (yt, db) ->
                            try {
                                updateStream(yt, db)
                            } catch(e: Exception) {
                                LOG.info("Error updating YouTube stream: $yt :: ${e.message}")
                                LOG.debug(e.stackTraceString)
                            }
                        }
                }

            } catch (e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }

            val runDuration = Duration.between(start, Instant.now())
            val delay = (90_000L..160_000L).random() - runDuration.toMillis()
            delay(max(delay, 0L))
        }
    }

    @WithinExposedContext
    private suspend fun updateStream(dbStream: DBYoutubeStreams.YoutubeStream, ytStream: Result<YoutubeVideoInfo, StreamErr>) {

        // if this stream has no remaining targets, delete it from DB to prevent further work
        if(untrackStaleEntity(dbStream.streamChannel)) {
            return
        }

        when(ytStream) {
            is Ok -> {
                val youtube = ytStream.value
                if(youtube.live) {
                    /* stream is still live - iterate all targets and make sure they have a notification
                     this is necessary because unlike the Twitch tracker, we do not update/post for all targets on every tick
                     (youtube does not provide information about the stream that would make it worth updating, such as a snapshot "thumbnail"
                     so if a stream is tracked in a different server/channel while live, it will not be posted
                     */
                    dbStream.streamChannel.targets.forEach { target ->

                        // check if target already has a notification
                        if(target.notifications.empty()) {
                            try {
                                createLiveNotification(youtube, target, new = false)
                            } catch(e: Exception) {
                                LOG.warn("Error while creating live notification for channel: ${youtube.channel} :: ${e.message}")
                                LOG.debug(e.stackTraceString)
                            }
                        }
                    }

                    // update viewer calculations
                    if(youtube.liveInfo?.concurrent != null) {
                        dbStream.currentViewers(youtube.liveInfo.concurrent)
                    } else {
                        LOG.warn("YouTube stream returned 'live' without details")
                    }

                } else {

                    // stream has ended and vod is available - edit notifications to reflect
                    val duration = youtube.duration?.run(::DurationFormatter)
                    val endTime = youtube.liveInfo?.endTime
                    val embedEdit: EmbedBlock = {
                        applyCommonFields(this, dbStream, endTime)
                        if(duration != null) setDescription("The video [${duration.colonTime}] is available.")
                        setTitle(youtube.title)
                        setThumbnail(youtube.thumbnail)
                    }
                    streamEnd(dbStream, embedEdit)
                }
            }
            is Err -> {
                when(ytStream.value) {
                    is StreamErr.IO -> return // this call failed due to network/API issue, don't act on this
                    is StreamErr.NotFound -> {

                        // this stream has ended and no vod is available (private or deleted) - edit notifications to reflect
                        // here, we can only provide information from our database
                        val lastTitle = dbStream.lastTitle
                        val embedEdit: EmbedBlock = {
                            applyCommonFields(this, dbStream, null)
                            setTitle("No VOD is available.")
                            setThumbnail(dbStream.lastThumbnail)
                            setDescription("Last video title: $lastTitle")
                        }
                        streamEnd(dbStream, embedEdit)
                    }
                }
            }
        }
    }

    @WithinExposedContext
    private fun applyCommonFields(spec: EmbedCreateSpec, dbStream: DBYoutubeStreams.YoutubeStream, endTime: Instant?) {
        val channelName = dbStream.lastChannelName
        val videoLink = "https://youtube.com/watch?v=${dbStream.youtubeVideoId}"
        val channelLink = "https://youtube.com/channel/${dbStream.streamChannel.siteChannelID}"

        spec.setAuthor("$channelName was live.", channelLink, dbStream.lastAvatar)
        spec.setUrl(videoLink)
        spec.setColor(inactiveColor)

        spec.addField("Peak viewers", dbStream.peakViewers.toString(), true)
        spec.addField("Average viewers", dbStream.averageViewers.toString(), true)

        val footer = if(endTime != null) "Stream ended " else "Stream ended (approximate) "
        val timestamp = endTime ?: Instant.now()
        spec.setFooter(footer, NettyFileServer.youtubeLogo)
        spec.setTimestamp(timestamp)
    }
}