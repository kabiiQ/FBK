package moe.kabii.trackers.videos.twitcasting.watcher

import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.twitcasting.Twitcasts
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.twitcasting.TwitcastingParser
import moe.kabii.trackers.videos.twitcasting.json.TwitcastingMovieResponse
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import java.time.Duration
import java.time.Instant

abstract class TwitcastNotifier(instances: DiscordInstances) : StreamWatcher(instances) {

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
        Twitcasts.TwitNotif.getForChannel(channel).forEach { notification ->
            val fbk = instances[notification.targetId.discordClient]
            val discord = fbk.client
            try {
                val dbMessage = notification.messageId
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake)
                    .awaitSingle()
                val features = getStreamConfig(notification.targetId)

                val action = if(features.summaries && info != null) {
                    val (movie, user) = info

                    val duration = Duration
                        .ofSeconds(movie.lengthSeconds.toLong())
                        .run(::DurationFormatter)
                        .colonTime

                    val endedEmbed = Embeds.other("${user.screenId} was live for [$duration]", inactiveColor)
                        .withAuthor(EmbedCreateFields.Author.of("${user.screenId} was live.", movie.link, user.imageUrl))
                        .withFields(EmbedCreateFields.Field.of("Views", movie.views.toString(), true))
                        .withUrl(movie.link)
                        .withFooter(EmbedCreateFields.Footer.of("Stream ended", NettyFileServer.twitcastingLogo))
                        .withTimestamp(Instant.now())
                        .withTitle(movie.title)
                        .withThumbnail(movie.thumbnailUrl)

                    existingNotif.edit()
                        .withEmbeds(endedEmbed)
                        .then(mono {
                            TrackerUtil.checkUnpin(existingNotif)
                        })

                } else {
                    existingNotif.delete()
                }

                action.thenReturn(Unit).tryAwait()
                checkAndRenameChannel(fbk.clientId, existingNotif.channel.awaitSingle(), endingStream = channel)

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

        val fbk = instances[target.discordClient]
        val discord = fbk.client
        // get target channel in discord
        val guildId = target.discordChannel.guild?.guildID
        val chan = getChannel(fbk, guildId, target.discordChannel.channelID, target)

        // get embed settings
        val guildConfig = guildId?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, this) }
        val features = getStreamConfig(target)

        // get mention role from db if one is registered
        val mention = if(guildId != null) {
            getMentionRoleFor(target, chan, features)
        } else null

        val (movie, user) = info
        try {
            val title = StringUtils.abbreviate(movie.title, MagicNumbers.Embed.TITLE)
            val desc = StringUtils.abbreviate(movie.subtitle, MagicNumbers.Embed.NORM_DESC) ?: ""

            val embed = Embeds.other(desc, liveColor)
                .withAuthor(EmbedCreateFields.Author.of("${user.name} (${user.screenId}) went live!", movie.link, user.imageUrl))
                .withUrl(movie.link)
                .withTitle(title)
                .withFooter(EmbedCreateFields.Footer.of("TwitCasting now! Since ", NettyFileServer.twitcastingLogo))
                .withTimestamp(movie.created)
                .run {
                    if(features.thumbnails) withImage(movie.thumbnailUrl) else withThumbnail(movie.thumbnailUrl)
                }

            val mentionMessage = if(mention != null) {

                val rolePart = if(mention.discord != null
                    && (mention.db.lastMention == null || org.joda.time.Duration(mention.db.lastMention, org.joda.time.Instant.now()) > org.joda.time.Duration.standardHours(6))) {

                    mention.db.lastMention = DateTime.now()
                    mention.discord.mention.plus(" ")
                } else ""
                val textPart = mention.db.mentionText?.plus(" ") ?: ""
                chan.createMessage("$rolePart$textPart")

            } else chan.createMessage()

            val newNotification = mentionMessage
                .withEmbeds(embed)
                .awaitSingle()

            TrackerUtil.pinActive(fbk, features, newNotification)
            TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

            // log notification in db
            Twitcasts.TwitNotif.new {
                this.targetId = target
                this.channelId = target.streamChannel
                this.messageId =  MessageHistory.Message.getOrInsert(newNotification)
            }

            checkAndRenameChannel(fbk.clientId, chan)
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                LOG.warn("Unable to send stream notification to channel: ${chan.id.asString()}. Disabling feature in channel. TwitcastNotifier.java")
                TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel, target::delete)
            } else throw ce
        }
    }
}