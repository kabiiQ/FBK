package moe.kabii.trackers.videos.kick.watcher

import discord4j.common.util.Snowflake
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.DBStreams
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.net.NettyFileServer
import moe.kabii.trackers.TrackerUtil
import moe.kabii.trackers.videos.StreamWatcher
import moe.kabii.trackers.videos.kick.parser.KickParser
import moe.kabii.trackers.videos.kick.parser.KickStreamInfo
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.*
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.Instant

abstract class KickNotifier(instances: DiscordInstances) : StreamWatcher(instances) {

    companion object {
        private val liveColor = KickParser.color
        private val inactiveColor = Color.of(2116880)
    }

    @CreatesExposedContext
    suspend fun streamStart(channel: TrackedStreams.StreamChannel, info: KickStreamInfo, targets: List<TrackedTarget>) {

        // create live stats object for stream
        val newEvent = propagateTransaction {
            if(DBStreams.LiveStreamEvent.getKickStreamFor(channel) != null) return@propagateTransaction null
            DBStreams.LiveStreamEvent.new {
                this.channelID = channel
                this.startTime = info.startTime.jodaDateTime
                this.peakViewers = info.viewers
                this.averageViewers = info.viewers
                this.uptimeTicks = 1
                this.lastTitle = info.title
                this.lastGame = info.category.name
            }

            val (noEvent, _) = eventManager.targets(channel)

            noEvent
                .forEach { target ->
                    eventManager.scheduleEvent(
                        target, info.url, info.title, info.category.name, null, null
                    )
                }
        }

        if(newEvent == null) {
            throw IllegalStateException("Duplicate live stream event for Kick stream: ${info.slug}")
        }
    }

    data class KickNotification(val discordClient: Int, val guildId: Snowflake?, val channelId: Snowflake, val messageId: Snowflake)
    @RequiresExposedContext
    suspend fun streamEnd(dbStream: DBStreams.LiveStreamEvent, info: KickStreamInfo) {

        val notifications = propagateTransaction {
            val (_, existingEvent) = eventManager.targets(dbStream.channelID)
            existingEvent
                .forEach { event ->
                    eventManager.completeEvent(event)
                }

            DBStreams.Notification
                .getForChannel(dbStream.channelID)
                .associateWith { notif ->
                    KickNotification(
                        notif.targetID.discordClient,
                        notif.targetID.discordChannel.guild?.guildID?.snowflake,
                        notif.messageID.channel.channelID.snowflake,
                        notif.messageID.messageID.snowflake
                    )
                }
        }

        // Update sent notifications with either 'summary' embed or deletion
        notifications.forEach { (dbNotif, data) ->

            val fbk = instances[data.discordClient]
            val discord = fbk.client

            discordTask {
                try {

                    val existingNotif = discord
                        .getMessageById(data.channelId, data.messageId)
                        .awaitSingle()
                    val features = getStreamConfig(data.discordClient, data.guildId, data.channelId)

                    val action = if(features.summaries) {
                        // edit live embed with post-stream summary
                        val uptime = Duration
                            .between(dbStream.startTime.javaInstant, Instant.now())
                            .run(::DurationFormatter)
                            .fullTime
                        val username = info.slug

                        val body = StringBuilder()
                        if(dbStream.lastTitle.isNotBlank()) {
                            body.append("Last stream title: ")
                                .append(dbStream.lastTitle)
                                .append('\n')
                        }
                        if(features.endGame) {
                            body.append("Last category: ")
                                .append(dbStream.lastGame)
                        }

                        val endedEmbed = Embeds.other(inactiveColor)
                            .withAuthor(EmbedCreateFields.Author.of("$username was live for $uptime.", info.url, null))
                            .withFooter(EmbedCreateFields.Footer.of("Stream ended ", NettyFileServer.kickLogo))
                            .withTimestamp(Instant.now())
                            .run {
                                if(body.isNotBlank()) withDescription(body.toString()) else this
                            }
                            .run {
                                if(features.viewers) {
                                    val viewers = "${dbStream.averageViewers} avg. / ${dbStream.peakViewers} peak"
                                    withFields(EmbedCreateFields.Field.of("Viewers", viewers, true))
                                } else this
                            }

                        existingNotif.edit()
                            .withEmbeds(endedEmbed)
                            .then(mono {
                                TrackerUtil.checkUnpin(existingNotif)
                            })

                    } else {
                        // no data available or summaries not enabled by this channel.
                        propagateTransaction {
                            existingNotif.delete()
                        }
                    }

                    action.thenReturn(Unit).tryAwait()
                    propagateTransaction {
                        checkAndRenameChannel(fbk.clientId, existingNotif.channel.awaitSingle(), endingStream = dbStream.channelID)
                    }

                } catch(ce: ClientException) {
                    LOG.info("Unable to get Kick stream notification $dbNotif :: ${ce.status.code()}")
                } catch(e: Exception) {
                    LOG.info("Error in Kick #streamEnd for channel ${info.slug} :: ${e.message}")
                    LOG.debug(e.stackTraceString)
                } finally {
                    propagateTransaction {
                        dbNotif.delete()
                    }
                }
            }
        }
    }

    suspend fun createLiveNotification(channel: TrackedStreams.StreamChannel, info: KickStreamInfo, target: TrackedTarget) {
        discordTask {
            val fbk = instances[target.discordClient]
            // get target channel in discord
                val chan = getChannel(fbk, target.discordGuild, target.discordChannel, target)

                // get embed settings
                val guildConfig = target.discordGuild?.run { GuildConfigurations.getOrCreateGuild(fbk.clientId, asLong()) }
                val features = getStreamConfig(target)

            // get mention role from db if one is registered
            val mention = if(target.discordGuild != null) {
                getMentionRoleFor(target, chan, features)
            } else null

            val title = StringUtils.abbreviate("${info.slug} live in category: ${info.category.name}", MagicNumbers.Embed.TITLE)
            val description = info.url

            try {
                val embed = Embeds.other(description, liveColor)
                    .withAuthor(
                        EmbedCreateFields.Author.of(
                            "${info.slug} went live!",
                            info.url,
                            null
                        )
                    )
                    .withTitle(title)
                    .withThumbnail(info.category.thumbnail)
                    .withUrl(info.url)
                    .withFooter(EmbedCreateFields.Footer.of("Live since ", NettyFileServer.kickLogo))
                    .withTimestamp(info.startTime)

                val mentionMessage = if (mention != null) {

                    val rolePart = if (mention.discord != null
                        && (mention.lastMention == null || org.joda.time.Duration(
                            mention.lastMention,
                            org.joda.time.Instant.now()
                        ) > org.joda.time.Duration.standardHours(1))
                    ) {

                        mention.discord.mention.plus(" ")
                    } else ""
                    val textPart = mention.textPart
                    val text = textPart?.run {
                        TrackerUtil.formatText(this, info.slug, info.startTime, info.userId.toString(), info.url)
                    } ?: ""
                    chan.createMessage("$rolePart$text")

                } else chan.createMessage()

                val newNotification = mentionMessage
                    .withEmbeds(embed)
                    .awaitSingle()

                TrackerUtil.pinActive(fbk, features, newNotification)
                TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

                // log notification in db
                propagateTransaction {
                    DBStreams.Notification.new {
                        this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                        this.targetID = target.findDBTarget()
                        this.channelID = channel
                        this.deleted = false
                    }

                    // edit channel name if feature is enabled and stream goes live
                    checkAndRenameChannel(fbk.clientId, chan)
                }
            } catch(ce: ClientException) {
                if(ce.status.code() == 403) {
                    LOG.warn("Unable to send kick stream notification to channel: ${chan.id.asString()}. Disabling feature in channel. KickNotifier.java")
                    TrackerUtil.permissionDenied(fbk, chan, FeatureChannel::streamTargetChannel) { target.findDBTarget().delete() }
                } else throw ce
            }
        }
    }
}