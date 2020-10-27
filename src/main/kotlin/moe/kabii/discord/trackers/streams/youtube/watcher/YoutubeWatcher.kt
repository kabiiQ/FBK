package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.StreamSettings
import moe.kabii.data.relational.discord.MessageHistory
import moe.kabii.data.relational.streams.DBYoutubeStreams
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.discord.trackers.streams.StreamWatcher
import moe.kabii.discord.trackers.streams.youtube.YoutubeVideoInfo
import moe.kabii.net.NettyFileServer
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.WithinExposedContext
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.stackTraceString
import moe.kabii.structure.extensions.success
import org.apache.commons.lang3.StringUtils

abstract class YoutubeWatcher(discord: GatewayDiscordClient) : StreamWatcher(discord) {

    @WithinExposedContext
    @Throws(ClientException::class)
    suspend fun createLiveNotification(liveStream: YoutubeVideoInfo, target: TrackedStreams.Target, new: Boolean = true): Message? {

        // get target channel in discord, make sure it still exists
        val chan = try {
            discord.getChannelById(target.discordChannel.channelID.snowflake).ofType(MessageChannel::class.java).awaitSingle()
        } catch(e: Exception) {
            if(e is ClientException && e.status.code() == 404) {
                // channel no longer exists, untrack
                target.delete()
                return null
            } else {
                LOG.warn("${Thread.currentThread().name} - YoutubeWatcher :: Unable to get Discord channel: ${e.message}")
                throw e
            }
        }

        // get channel stream embed settings
        val guildId = target.discordChannel.guild?.guildID
        val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
        val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).streamSettings }
            ?: StreamSettings() // use default settings for pm notifications

        // get mention role from db if one is registered
        val mentionRole = if(guildId != null) { 
            getMentionRoleFor(target.streamChannel, guildId, chan)
        } else null

        val mention = mentionRole?.mention
        try {
            val shortDescription = StringUtils.abbreviate(liveStream.description, 150)
            val startTime = liveStream.liveInfo?.startTime
            val sinceStr = if(startTime != null) " since " else " "

            val newNotification = chan.createMessage { spec ->
                if(mention != null && guildConfig!!.guildSettings.followRoles) spec.setContent(mention)
                val embed: EmbedBlock = {
                    val liveMessage = if(new) " went live!" else " is live."
                    setAuthor("${liveStream.channel.name}$liveMessage", liveStream.url, liveStream.channel.avatar)
                    setUrl(liveStream.url)
                    setColor(YoutubeTarget.serviceColor)
                    setTitle(liveStream.title)
                    setDescription(shortDescription)
                    if(features.thumbnails) setImage(liveStream.thumbnail)
                    setFooter("Live on YouTube$sinceStr", NettyFileServer.youtubeLogo)
                    if(startTime != null) {
                        setTimestamp(startTime)
                    }
                }
                spec.setEmbed(embed)
            }.awaitSingle()

            // log message in db
            TrackedStreams.Notification.new {
                this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                this.targetID = target
                this.channelID = target.streamChannel
            }

            // edit channel name if feature is enabled and stream starts
            checkAndRenameChannel(chan)

            return newNotification

        } catch (ce: ClientException) {
            val err = ce.status.code()
            if(err == 404 || err == 403) {
                // notification has been deleted or we don't have perms to send. untrack to avoid further errors
                LOG.info("Unable to send stream notification to channel '${chan.id.asString()}'. Untracking target :: $target")
                target.delete()
                return null
            } else throw ce
        }
    }

    suspend fun streamEnd(dbStream: DBYoutubeStreams.YoutubeStream, embedEdit: EmbedBlock) {
        // edit/delete all notifications and remove stream from db when stream ends
        dbStream.streamChannel.notifications.forEach { notification ->
            try {
                val dbMessage = notification.messageID
                val existingNotif = discord.getMessageById(dbMessage.channel.channelID.snowflake, dbMessage.messageID.snowflake).awaitSingle()

                // get channel settings so we can respect config to edit or delete
                val guildId = existingNotif.guildId.orNull()
                val findFeatures = if(guildId != null) {
                    val config = GuildConfigurations.getOrCreateGuild(guildId.asLong())
                    config.options.featureChannels[existingNotif.channelId.asLong()]?.streamSettings
                } else null
                val features = findFeatures ?: StreamSettings()

                if(features.summaries) {
                    existingNotif.edit { msg ->
                        msg.setEmbed(embedEdit)
                    }
                } else {
                    existingNotif.delete()
                }.then().success().awaitSingle()

                checkAndRenameChannel(existingNotif.channel.awaitSingle(), endingStream = notification)
            } catch(ce: ClientException) {
                LOG.info("Unable to find YouTube stream notification $notification :: ${ce.status.code()}")
            } catch(e: Exception) {
                LOG.info("Error in YouTube #streamEnd for stream $dbStream :: ${e.message}")
                LOG.debug(e.stackTraceString)
            } finally {
                // delete the notification from db either way, we are done with it
                notification.delete()
            }
        }

        // delete live stream event for this channel
        dbStream.delete()
    }
}