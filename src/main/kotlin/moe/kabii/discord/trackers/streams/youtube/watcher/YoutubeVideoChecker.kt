package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.rest.util.Color
import kotlinx.coroutines.runBlocking
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TwitchSettings
import moe.kabii.data.relational.DBYoutubeStreams
import moe.kabii.discord.trackers.streams.StreamErr
import moe.kabii.discord.trackers.streams.youtube.YoutubeParser
import moe.kabii.discord.trackers.streams.youtube.YoutubeVideoInfo
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.rusty.Result
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.*
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
                runBlocking {
                    newSuspendedTransaction {
                        DBYoutubeStreams.YoutubeStream.all()
                            .asSequence()
                            .chunked(50)
                            .flatMap { chunk ->
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

        // if this stream has no remaining targets, do not call API, and delete it from DB to prevent further work
        if(untrackChannelIfDead(dbStream.streamChannel)) {
            return
        }

        when(ytStream) {
            is Ok -> {
                val youtube = ytStream.value
                if(youtube.live) {
                    /* stream is still live - iterate all targets and make sure they have a notification
                     this is necessary because unlike the Twitch tracker, we do not update/post for all targets on every tick
                     (youtube does not provide information about the stream that would make it worth updating, such as current view count)
                     so if a stream is tracked in a different server/channel while live, it will not be posted
                     */
                    newSuspendedTransaction {
                        dbStream.streamChannel.targets.forEach { target ->

                            // check if target already has a notification
                            if(target.notifications.empty()) {
                                try {
                                    createLiveNotification(youtube, target, new = false)
                                } catch(e: Exception) {
                                    LOG.error("Non-Discord error while creating live notification for channel: ${youtube.channel} :: ${e.message}")
                                    LOG.debug(e.stackTraceString)
                                }
                            }
                        }
                    }


                } else {

                    // stream has ended and vod is available - edit notifications to reflect
                    val duration = youtube.duration?.run(::DurationFormatter)?.fullTime
                    val embedEdit: EmbedBlock = {
                        setAuthor("${youtube.channel.name} was live.", youtube.url, youtube.channel.avatar)
                        if(duration != null) setDescription("A VOD is available with $duration of video.")
                        setUrl(youtube.url)
                        setColor(inactiveColor)
                        setTitle(youtube.title)
                        setFooter("YouTube video", NettyFileServer.youtubeLogo)
                    }
                    streamEnd(dbStream, embedEdit)
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
                            val embedEdit: EmbedBlock = {
                                setAuthor("$channelName was live.", channelLink, null)
                                setUrl(videoLink)
                                setColor(inactiveColor)
                                setTitle("No VOD is available.")
                                setDescription("Last video title: $lastTitle")
                            }
                            streamEnd(dbStream, embedEdit)
                        }
                    }
                }
            }
        }


    }

    private suspend fun streamEnd(dbStream: DBYoutubeStreams.YoutubeStream, embedEdit: EmbedBlock) {
        // edit/delete all notifications and remove stream from db when stream ends
        newSuspendedTransaction {
            dbStream.streamChannel.notifications.forEach { notification ->
                val dbMessage = notification.messageID
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).tryAwait().orNull()
                if(existingNotif != null) {

                    // get channel settings so we can respect config to edit or delete
                    val guildId = existingNotif.guildId.orNull()
                    val findFeatures = if(guildId != null) {
                        val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())
                        config.options.featureChannels[existingNotif.channelId.asLong()]?.twitchSettings
                    } else null
                    val features = findFeatures ?: TwitchSettings()

                    if(features.summaries) {
                        existingNotif.edit { msg ->
                            msg.setEmbed(embedEdit)
                        }
                    } else {
                        existingNotif.delete()
                    }.tryAwait().orNull()
                }
                // delete the notification from db either way, we are done with it
                notification.delete()
            }

            // delete live stream event for this channel
            dbStream.delete()
        }
    }
}