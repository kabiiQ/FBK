package moe.kabii.discord.trackers.streams.youtube.watcher

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.rest.http.client.ClientException
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.TwitchSettings
import moe.kabii.data.relational.DBYoutubeStreams
import moe.kabii.data.relational.MessageHistory
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.trackers.YoutubeTarget
import moe.kabii.discord.trackers.streams.youtube.YoutubeVideoInfo
import moe.kabii.net.NettyFileServer
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.EmbedBlock
import moe.kabii.structure.extensions.orNull
import moe.kabii.structure.extensions.snowflake
import moe.kabii.structure.extensions.tryAwait
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

abstract class YoutubeWatcher(val discord: GatewayDiscordClient) {

    suspend fun createLiveNotification(liveStream: YoutubeVideoInfo, target: TrackedStreams.Target, new: Boolean = true): Message? {

        // get channel stream embed settings
        val guildId = target.discordChannel.guild?.guildID
        val guildConfig = guildId?.run(GuildConfigurations::getOrCreateGuild)
        val features = guildConfig?.run { getOrCreateFeatures(target.discordChannel.channelID).twitchSettings }
            ?: TwitchSettings() // use default settings for pm notifications

        // get target channel in discord, make sure it still exists
        val disChan = discord.getChannelById(target.discordChannel.channelID.snowflake)
            .ofType(MessageChannel::class.java)
            .tryAwait()
        val chan = when(disChan) {
            is Ok -> disChan.value
            is Err -> {
                val err = disChan.value
                if(err is ClientException && err.status.code() == 404) {
                    // channel no longer exists, untrack
                    target.delete()
                } // else retry next tick
                return null
            }
        }

        // get mention role from db if one is registered
        val mentionRole = if(guildId != null) {
            TrackedStreams.Mention.getMentionRoleFor(target.streamChannel, guildId, chan)
        } else null

        val mention = mentionRole?.mention
        try {
            val shortDescription = StringUtils.abbreviate(liveStream.description, 150)

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
                    setFooter("Live on YouTube", NettyFileServer.youtubeLogo)
                }
                spec.setEmbed(embed)
            }.awaitSingle()

            // log message in db
            TrackedStreams.Notification.new {
                this.messageID = MessageHistory.Message.getOrInsert(newNotification)
                this.targetID = target
                this.channelID = target.streamChannel
            }

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

    suspend fun untrackChannelIfDead(channel: TrackedStreams.StreamChannel): Boolean {
        return newSuspendedTransaction {
            if(channel.targets.empty()) {
                channel.delete()
                LOG.info("Untracking ${channel.site.targetType.full} channel: ${channel.siteChannelID} as it has no targets.")
                true
            } else false
        }
    }
}