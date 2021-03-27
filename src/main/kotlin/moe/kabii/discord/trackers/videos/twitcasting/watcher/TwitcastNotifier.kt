package moe.kabii.discord.trackers.videos.twitcasting.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitcasting.Twitcasts
import moe.kabii.discord.trackers.TrackerUtil
import moe.kabii.discord.trackers.videos.StreamWatcher
import moe.kabii.discord.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.discord.trackers.videos.twitcasting.json.TwitcastingMovieResponse
import moe.kabii.net.NettyFileServer
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

abstract class TwitcastNotifier(discord: GatewayDiscordClient) : StreamWatcher(discord) {

    companion object {
        private val liveColor = TwitcastingParser.color
        private val inactiveColor = Color.of(812958)
    }

    @WithinExposedContext
    suspend fun movieLive(channel: TrackedStreams.StreamChannel, info: TwitcastingMovieResponse, targets: List<TrackedStreams.Target>) {
        val (movie, user) = info

        // create db movie - this method is only called at the real beginning of a stream
        val existing = Twitcasts.Movie.getMovieFor(user.userId)
        if(existing != null) {
            LOG.warn("Duplicate Twitcast broadcast for user ${user.userId}: ${existing.movieId} + ${movie.movieId}")
            return
        }
        Twitcasts.Movie.new {
            this.channel = channel
            this.movieId = movie.movieId
        }

        targets.forEach { target ->
            try {
                createLiveNotification(info, target)
            } catch(e: Exception) {
                LOG.warn("Error while sending live notification for channel: $channel :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    @WithinExposedContext
    suspend fun movieEnd(dbMovie: Twitcasts.Movie, info: TwitcastingMovieResponse?) {
        val channel = dbMovie.channel
        Twitcasts.Notification.getForChannel(channel).forEach { notification ->
            try {
                val dbMessage = notification.messageId
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake)
                    .awaitSingle()
                val features = getStreamConfig(notification.targetId)

                if(features.summaries && info != null) {
                    val (movie, user) = info

                    val duration = Duration
                        .ofSeconds(movie.lengthSeconds.toLong())
                        .run(::DurationFormatter)
                        .colonTime

                    existingNotif.edit { spec ->
                        spec.setEmbed { embed ->
                            embed.setColor(inactiveColor)
                            embed.setAuthor("${user.screenId} was live.", movie.link, user.imageUrl)
                            embed.addField("Views", movie.views.toString(), true)
                            embed.setUrl(movie.link)
                            embed.setFooter("Stream ended", NettyFileServer.twitcastingLogo)
                            embed.setTimestamp(Instant.now())
                            embed.setDescription("${user.screenId} was live for [$duration]")
                            embed.setTitle(movie.title)
                            embed.setThumbnail(movie.thumbnailUrl)
                        }
                    }

                } else {
                    existingNotif.delete()
                }.then().success().awaitSingle()

                checkAndRenameChannel(existingNotif.channel.awaitSingle(), endingStream = channel)

            } catch(ce: ClientException) {
                LOG.info("Unable to get Twitcast stream notification $notification :: ${ce.status.code()}")
            } catch(e: Exception) {
                LOG.info("Error in Twitcasting #streamEnd for movie $dbMovie :: ${e.message}")
                LOG.debug(e.stackTraceString)
            } finally {
                notification.delete()
            }
        }
        dbMovie.delete()
    }

    @WithinExposedContext
    suspend fun createLiveNotification(info: TwitcastingMovieResponse, target: TrackedStreams.Target) {

        // get target channel in discord
        val guildId = target.discordChannel.guild?.guildID
        val chan = getChannel(guildId, target.discordChannel.channelID, FeatureChannel::streamsChannel, target)

        // get embed settings
        val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
        val features = getStreamConfig(target)

        // get mention role from db if one is registered
        val mentionRole = if(guildId != null) {
            getMentionRoleFor(target.streamChannel, guildId, chan)
        } else null
        val mention = mentionRole?.mention

        val (movie, user) = info
        try {
            val title = StringUtils.abbreviate(movie.title, MagicNumbers.Embed.TITLE)
            val desc = StringUtils.abbreviate(movie.subtitle, MagicNumbers.Embed.DESC) ?: ""

            val newNotification = chan.createMessage { spec ->
                if(mention != null) spec.setContent(mention)

                val embed: EmbedBlock = {
                    setColor(liveColor)
                    setAuthor("${user.name} (${user.screenId}) went live!", movie.link, user.imageUrl)
                    setUrl(movie.link)
                    setTitle(title)
                    setDescription(desc)
                    if(features.thumbnails) setImage(movie.thumbnailUrl)else setThumbnail(movie.thumbnailUrl)
                    setFooter("TwitCasting now! Since ", NettyFileServer.twitcastingLogo)
                    setTimestamp(movie.created)
                }
                spec.setEmbed(embed)
            }.awaitSingle()
            TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

            // log notification in db
            Twitcasts.Notification.new {
                this.targetId = target
                this.channelId = target.streamChannel
                this.messageId =  MessageHistory.Message.getOrInsert(newNotification)
            }

            checkAndRenameChannel(chan)
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                LOG.warn("Unable to send stream notification to channel: ${chan.id.asString()}. Disabling feature in channel. TwitcastNotifier.java")
                TrackerUtil.permissionDenied(chan, FeatureChannel::streamsChannel, target::delete)
            } else throw ce
        }
    }
}