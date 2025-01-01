package moe.kabii.command.commands.trackers.track.relay

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.commands.trackers.util.TargetSuggestionGenerator
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.relational.discord.DiscordObjects
import moe.kabii.data.relational.streams.youtube.YoutubeVideo
import moe.kabii.data.relational.streams.youtube.ytchat.YoutubeLiveChat
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.videos.youtube.YoutubeParser
import moe.kabii.util.constants.URLUtil
import moe.kabii.util.extensions.propagateTransaction

object ManualRelayVideo {
    suspend fun track(origin: DiscordParameters, videoId: String) {
        // Attempt intake of video
        // Video may already be known, check database before calling out
        val chatWatcher = origin.handler.instances.services.ytChatWatcher

        var dbVideo: YoutubeVideo? = null
        var chat = false
        var supported = false
        propagateTransaction {
            val video = YoutubeVideo.getVideo(videoId)
            if(video != null) {
                dbVideo = video
                chat = video.scheduledEvent != null || video.liveEvent != null
                supported = chatWatcher.supportedChannel(video.ytChannel.siteChannelID)
            }
        }

        if(dbVideo == null) {
            // video not in database, call out to youtube
            val video = YoutubeParser.getVideo(videoId)
            if(video == null) {
                origin.ereply(Embeds.error("Invalid YouTube video ID **$videoId**.")).awaitSingle()
                return
            }
            propagateTransaction {
                val new = YoutubeVideo.getOrInsert(videoId, video.channel.id)
                // db will not have information on upcoming/live yet - get from api call response
                dbVideo = new
                chat = video.upcoming || video.live
                supported = chatWatcher.supportedChannel(video.channel.id)
            }
        }

        if(!supported) {
            ChatRelayCommand.sendLimitedError(origin)
            return
        }

        if(!chat) {
            origin.ereply(Embeds.error("[$videoId](${URLUtil.StreamingSites.Youtube.video(videoId)}) does not seem to have an active chat room.")).awaitSingle()
            return
        }

        // video exists and is live or scheduled. record chat relay into database
        propagateTransaction {
            YoutubeLiveChat.new {
                this.ytVideo = dbVideo!!
                this.discordClient = origin.client.clientId
                this.discordChannel = DiscordObjects.Channel.getOrInsert(origin.chan.id.asLong(), origin.guild?.id?.asLong())
            }
        }

        // notify HoloChats to immediately begin watching chat for messages
        chatWatcher.holoChats.subscribeChat(dbVideo!!.videoId, origin.chan)

        origin.ireply(Embeds.fbk("Now watching for chat messages in [$videoId](${URLUtil.StreamingSites.Youtube.video(videoId)}).")).awaitSingle()
    }

    suspend fun untrack(origin: DiscordParameters, videoId: String) {
        // Get relayed video from database
        val dbChat = propagateTransaction {
            YoutubeLiveChat.getTrackedChat(videoId, origin.chan.id.asLong())
        }

        if(dbChat == null) {
            origin.ereply(Embeds.error("**${videoId}** is not a manually tracked HoloChat relay in this channel.")).awaitSingle()
            return
        }

        // Video relay exists in this channel, can simply remove now
        // There is no ownership of HoloChat relays like with the other trackers, so no checks are really needed here
        propagateTransaction {
            dbChat.delete()
        }
        origin.ireply(Embeds.fbk("No longer tracking HoloChat relays for [$videoId](${URLUtil.StreamingSites.Youtube.video(videoId)}) in this channel.")).awaitSingle()
        TargetSuggestionGenerator.invalidateTargets(origin.client.clientId, origin.chan.id.asLong())
    }
}