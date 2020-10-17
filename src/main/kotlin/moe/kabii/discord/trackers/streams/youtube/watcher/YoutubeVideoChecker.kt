package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.rest.util.Color
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.data.relational.DBYoutubeStreams
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.trackers.streams.youtube.YoutubeParser
import moe.kabii.discord.trackers.streams.youtube.YoutubeVideoInfo
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.loop
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.tryAwait
import moe.kabii.util.DurationFormatter
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class YoutubeVideoChecker(val discord: GatewayDiscordClient) : Runnable {
    companion object {
        private val inactiveColor = Color.of(8847360)
    }

    override fun run() {
        loop {
            val start = Instant.now()

            // check known live streams, can group up to 50 video IDs in an API call
            try {
                runBlocking {
                    transaction {
                        DBYoutubeStreams.YoutubeStream.all()
                    }.asSequence().chunked(50).flatMap { chunk ->
                        // chunk, call API, and match api response back to to database object
                        val chunkIds = chunk.map(DBYoutubeStreams.YoutubeStream::youtubeVideoId)
                        val videos = YoutubeParser.getVideos(chunkIds)
                        chunk.associateWith { stream ->
                            videos.getValue(stream.youtubeVideoId)
                        }.entries
                    }.forEach { (yt, db) ->
                        updateStream(yt, db)
                    }
                }

            } catch (e: Exception) {
                LOG.error("Uncaught exception in ${Thread.currentThread().name} :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }

            val runDuration = Duration.between(start, Instant.now())
            val delay = 90_000L - runDuration.toMillis()
            Thread.sleep(max(delay, 0L))
        }
    }

    private suspend fun updateStream(dbStream: DBYoutubeStreams.YoutubeStream, ytStream: Result<YoutubeVideoInfo, StreamErr>) {

        val embedEdit: EmbedBlock = when(ytStream) {
            is Ok -> {
                val youtube = ytStream.value
                // todo add embed created if target does not have notification
                if(youtube.live) return // stream is still live

                // stream has ended and vod is available - edit notifications to reflect
                val duration = youtube.duration?.run(::DurationFormatter)?.fullTime
                {
                    setAuthor("${youtube.channel.name} was live.", youtube.url, youtube.channel.avatar)
                    if(duration != null) setDescription("A VOD is available with $duration of video.")
                    setUrl(youtube.url)
                    setColor(inactiveColor)
                    setTitle(youtube.title)
                    setFooter("YouTube video", NettyFileServer.youtubeLogo)
                }

            }
            is Err -> {
                when(ytStream.value) {
                    is StreamErr.IO -> return // this call failed due to network/API issue, don't act on this
                    is StreamErr.NotFound -> {

                        // this stream has ended and no vod is available (private or deleted) - edit notifications to reflect
                        newSuspendedTransaction {
                            // here, we can only provide information from our database
                            val channelName = dbStream.lastChannelName
                            val videoLink = "https://youtube.com/watch?v=${dbStream.youtubeVideoId}"
                            val channelLink = "https://youtube.com/channel/${dbStream.streamChannel.siteChannelID}"
                            val lastTitle = dbStream.lastTitle
                            {
                                setAuthor("$channelName was live.", channelLink, null)
                                setUrl(videoLink)
                                setColor(inactiveColor)
                                setTitle("No VOD is available.")
                                setDescription("Last video title: $lastTitle")
                            }
                        }
                    }
                }
            }
        }

        // edit/post a notification in every track target
        // todo iterate targets here rather than notifications
        newSuspendedTransaction {
            dbStream.streamChannel.notifications.forEach { notification ->
                val dbMessage = notification.messageID
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).tryAwait().orNull()
                if(existingNotif != null) {
                    existingNotif.edit { msg ->
                        msg.setEmbed(embedEdit)
                    }.tryAwait().orNull()
                }
                // delete the notification from db either way, we are done with it
                notification.delete()
            }

            // remove stream from LIVE db
            dbStream.delete()
        }
    }
}