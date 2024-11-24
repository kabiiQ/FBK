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
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

abstract class TwitcastNotifier(instances: DiscordInstances) : StreamWatcher(instances) {

    companion object {
        private val liveColor = TwitcastingParser.color
        private val inactiveColor = Color.of(812958)
    }

    @RequiresExposedContext
    suspend fun movieLive(channel: TrackedStreams.StreamChannel, info: TwitcastingMovieResponse, targets: List<TrackedTarget>) {
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

    @RequiresExposedContext
    suspend fun movieEnd(dbMovie: Twitcasts.Movie, info: TwitcastingMovieResponse?) {
        val channel = dbMovie.channel

        val (_, existingEvent) = eventManager.targets(channel)
        existingEvent
            .forEach { event ->
                eventManager.completeEvent(event)
            }

        Twitcasts.TwitNotif.getForChannel(channel).forEach { notification ->
            val fbk = instances[notification.targetId.discordClient]
            val discord = fbk.client
            try {
                val notifChan = notification.messageId.channel.channelID.snowflake
                val notifMessage = notification.messageId.messageID.snowflake
                val features = getStreamConfig(notification.targetId)
                discordTask {
                    val existingNotif = discord.getMessageById(notifChan, notifMessage)
                        .awaitSingle()

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
                    propagateTransaction {
                        checkAndRenameChannel(fbk.clientId, existingNotif.channel.awaitSingle(), endingStream = channel)
                    }
                }

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

    @CreatesExposedContext
    suspend fun createLiveNotification(info: TwitcastingMovieResponse, target: TrackedTarget) {

        val fbk = instances[target.discordClient]
        // get target channel in discord

        // get embed settings
        val guildConfig = target.discordGuild?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, asLong()) }
        val features = getStreamConfig(target)

        discordTask {
            val chan = getChannel(fbk, target.discordGuild, target.discordChannel, target)
            // get mention role from db if one is registered
            val mention = if(target.discordGuild != null) {
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
                        && (mention.lastMention == null || org.joda.time.Duration(mention.lastMention, org.joda.time.Instant.now()) > org.joda.time.Duration.standardHours(1))) {

                        mention.discord.mention.plus(" ")
                    } else ""
                    val textPart = mention.textPart
                    val text = textPart?.run {
                        TrackerUtil.formatText(this,  user.name, movie.created, info.broadcaster.userId, user.url)
                    } ?: ""
                    chan.createMessage("$rolePart$text")

                } else chan.createMessage()

                val newNotification = mentionMessage
                    .withEmbeds(embed)
                    .awaitSingle()

                TrackerUtil.pinActive(fbk, features, newNotification)
                TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

                propagateTransaction {
                    // log notification in db
                    Twitcasts.TwitNotif.new {
                        this.targetId = target.findDBTarget()
                        this.channelId = TrackedStreams.StreamChannel.findById(target.dbStream)!!
                        this.messageId =  MessageHistory.Message.getOrInsert(newNotification)
                    }

                    checkAndRenameChannel(fbk.clientId, chan)
                }
            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    LOG.warn("Unable to send stream notification to channel: ${chan.id.asString()}. Disabling feature in channel. TwitcastNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                } else throw ce
            }
        }
    }
}