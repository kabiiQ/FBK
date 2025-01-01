package moe.kabii.command.commands.trackers.track.relay

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.TrackedStreams
import moe.kabii.data.relational.streams.youtube.ytchat.LiveChatConfiguration
import moe.kabii.discord.util.Embeds
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction

object AutoRelayChannel {
    suspend fun track(origin: DiscordParameters, channelId: String) {
        // Validate YT channel from local DB, must already be tracked
        // This limitation is acceptable, chat relay will certainly be a channel also already tracked for livestreams
        val dbChannel = propagateTransaction {
            TrackedStreams.StreamChannel.getChannel(TrackedStreams.DBSite.YOUTUBE, channelId)
        }

        val chatWatcher = origin.handler.instances.services.ytChatWatcher
        val supported = chatWatcher.supportedChannel(channelId)
        if(dbChannel == null || !supported) {
            ChatRelayCommand.sendLimitedError(origin)
            return
        }

        propagateTransaction {
            LiveChatConfiguration.new {
                this.discordClient = origin.client.clientId
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
                this.chatChannel = dbChannel
            }
        }

        // Notify HoloChats service
        chatWatcher.holoChats.subscribeChannel(channelId, origin.chan)

        origin.ireply(Embeds.fbk("Now relaying chat messages in livestreams from [$channelId](${URLUtil.StreamingSites.Youtube.channel(channelId)}).")).awaitSingle()
    }

    suspend fun untrack(origin: DiscordParameters, channelId: String) {
        // Get relayed channel from database
        val dbRelay = propagateTransaction {
            LiveChatConfiguration.getConfiguration(channelId, origin.chan.id.asLong())
        }

        if(dbRelay == null) {
            origin.ereply(Embeds.error("**${channelId}** is not a HoloChat relay in this channel.")).awaitSingle()
            return
        }

        // Delete relay configuration, there is no ownership to be checked
        val username = propagateTransaction {
            val username = dbRelay.chatChannel.lastKnownUsername
            dbRelay.delete()
            username
        }

        origin.ireply(Embeds.fbk("No longer tracking HoloChat relays for livestreams from [$username](${URLUtil.StreamingSites.Youtube.channel(channelId)}) in this channel.")).awaitSingle()
        TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
    }
}