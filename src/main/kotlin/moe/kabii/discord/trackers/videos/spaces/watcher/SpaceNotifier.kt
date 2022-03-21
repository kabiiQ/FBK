package moe.kabii.discord.trackers.videos.spaces.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.spaces.TwitterSpaces
import moe.kabii.discord.trackers.TrackerUtil
import moe.kabii.discord.trackers.twitter.TwitterParser
import moe.kabii.discord.trackers.twitter.json.TwitterSpace
import moe.kabii.discord.trackers.videos.StreamWatcher
import moe.kabii.net.NettyFileServer
import moe.kabii.util.DurationFormatter
import moe.kabii.util.constants.MagicNumbers
import moe.kabii.util.extensions.WithinExposedContext
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.stackTraceString
import moe.kabii.util.extensions.tryAwait
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.Instant

abstract class SpaceNotifier(discord: GatewayDiscordClient) : StreamWatcher(discord) {

    companion object {
        private val liveColor = TwitterParser.color
        private val inactiveColor = Color.of(812958)
    }

    @WithinExposedContext
    suspend fun updateLiveSpace(dbChannel: TrackedStreams.StreamChannel, space: TwitterSpace, targets: List<TrackedStreams.Target>) {

        val dbSpace = TwitterSpaces.Space.getOrInsert(dbChannel, space.id)

        val notifs = TwitterSpaces.SpaceNotif.getForSpace(dbSpace)

        // check all targets have notification (handles new spaces and new tracks)
        targets.forEach { target ->
            if(notifs.find { notif -> notif.targetId == target } != null) return@forEach
            try {
                createSpaceNotification(space, dbSpace, target)
            } catch(e: Exception) {
                LOG.warn("Error while sending space notification for channel $dbChannel :: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }

    @WithinExposedContext
    suspend fun createSpaceNotification(space: TwitterSpace, dbSpace: TwitterSpaces.Space, target: TrackedStreams.Target) {
        // get discord channel
        val guildId = target.discordChannel.guild?.guildID
        val chan = getChannel(guildId, target.discordChannel.channelID, FeatureChannel::streamTargetChannel, target)

        val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
        val features = getStreamConfig(target)

        val mention = guildId?.run { getMentionRoleFor(target.streamChannel, this, chan, features) }

        try {
            val title = StringUtils.abbreviate(space.title, MagicNumbers.Embed.TITLE)

            val newNotification = chan.createMessage { spec ->
                if(mention != null) {
                    if(mention.db.lastMention == null
                        || Duration(mention.db.lastMention, Instant.now()) > Duration.standardHours(6)) {
                        spec.setContent(mention.discord.mention)
                        mention.db.lastMention = DateTime.now()
                    }
                }

                val hosts = space.hosts - space.creator

                spec.setEmbed { embed ->
                    embed.setColor(liveColor)
                    embed.setAuthor("@${space.creator?.username} started a Space!", space.url, space.creator?.profileImage)
                    embed.setTitle(StringUtils.abbreviate(space.title, MagicNumbers.Embed.TITLE))
                    embed.setUrl(space.url)
                    if(hosts.isNotEmpty()) {
                        val hostUsers = hosts.joinToString("\n") { host -> "@${host!!.username}"}
                        embed.setDescription("Other Space hosts:\n$hostUsers")
                    }
                    embed.setFooter("Live now! Since ", NettyFileServer.twitterLogo)
                    space.startedAt?.run(embed::setTimestamp)
                }
            }.awaitSingle()

            TrackerUtil.pinActive(discord, features, newNotification)
            TrackerUtil.checkAndPublish(newNotification, guildConfig?.guildSettings)

            TwitterSpaces.SpaceNotif.new {
                this.targetId = target
                this.spaceId = dbSpace
                this.message = MessageHistory.Message.getOrInsert(newNotification)
            }

            checkAndRenameChannel(chan)
        } catch(ce: ClientException) {
            if(ce.status.code() == 403) {
                LOG.warn("Unable to send Space notification to channel: ${chan.id.asString()}. Disabling feature in channel. SpaceNotifier.java")
                TrackerUtil.permissionDenied(chan, FeatureChannel::streamTargetChannel, target::delete)
            } else throw ce
        }
    }


    @WithinExposedContext
    suspend fun updateEndedSpace(dbChannel: TrackedStreams.StreamChannel, space: TwitterSpace) {

        // check if space in db (or ignore)
        val dbSpace = TwitterSpaces.Space
            .find { TwitterSpaces.Spaces.spaceId eq space.id }
            .firstOrNull() ?: return

        TwitterSpaces.SpaceNotif.getForSpace(dbSpace).forEach { notification ->
            try {
                val dbMessage = notification.message
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake)
                    .awaitSingle()
                val features = getStreamConfig(notification.targetId)

                val action = if(features.summaries) {
                    existingNotif.edit { spec ->
                        spec.setEmbed { embed ->

                            embed.setColor(inactiveColor)
                            embed.setAuthor("@${space.creator?.username} was live.", space.url, space.creator?.profileImage)
                            embed.addField("Participants ", space.participants.toString(), true)
                            embed.setUrl(space.url)
                            embed.setFooter("Space ended", NettyFileServer.twitterLogo)
                            space.endedAt?.run(embed::setTimestamp)
                            embed.setTitle(StringUtils.abbreviate(space.title, MagicNumbers.Embed.TITLE))

                            if(space.startedAt != null && space.endedAt != null) {
                                val duration = java.time.Duration
                                    .between(space.startedAt, space.endedAt)
                                    .run(::DurationFormatter)
                                    .colonTime
                                embed.setDescription("@${space.creator?.username} was live for [$duration]")
                            }
                        }
                    }.then(mono {
                        TrackerUtil.checkUnpin(existingNotif)
                    })
                } else {
                    existingNotif.delete()
                }

                action.thenReturn(Unit).tryAwait()
                checkAndRenameChannel(existingNotif.channel.awaitSingle(), endingStream = dbChannel)
            } catch(ce: ClientException) {
                LOG.info("Unable to get Space notification $notification :: ${ce.status.code()}")
            } catch(e: Exception) {
                LOG.info("Error in Space #streamEnd for space $dbSpace :: ${e.message}")
                LOG.debug(e.stackTraceString)
            } finally {
                notification.delete()
            }
        }

        dbSpace.delete()
    }
}