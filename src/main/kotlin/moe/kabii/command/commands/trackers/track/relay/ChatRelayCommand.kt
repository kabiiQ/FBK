package moe.kabii.command.commands.trackers.track.relay

import discord4j.core.`object`.entity.channel.GuildMessageChannel
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.commands.trackers.track.TrackerCommand
import moe.kabii.command.commands.trackers.track.TrackerCommandBase
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.util.Embeds
import moe.kabii.trackers.HoloChatsTarget
import moe.kabii.trackers.TargetArguments
import moe.kabii.trackers.TrackerTarget
import moe.kabii.trackers.videos.youtube.YoutubeParser

object ChatRelayCommand : TrackerCommand {
    sealed class RelaySpec
    data class ChannelRelaySpec(val channelId: String) : RelaySpec()
    data class VideoRelaySpec(val videoId: String) : RelaySpec()

    // Identify request as either a YT channel or video ID
    private fun parseRelay(id: String): RelaySpec? {
        val channelMatch = YoutubeParser.matchChannelId(id)
        if(channelMatch != null) return ChannelRelaySpec(channelMatch)
        val videoMatch = YoutubeParser.matchVideoId(id)
        if(videoMatch != null) return VideoRelaySpec(videoMatch)
        return null
    }

    private suspend fun sendFormatError(origin: DiscordParameters, id: String) =
        origin
            .ereply(Embeds.error("Invalid HoloChats target '$id'. Specify a YouTube 24-character channel ID for configuring relays or a video ID to force relay of a specific video.\n\nYouTube channel handles are not currently supported for this command."))
            .awaitSingle()

    suspend fun sendLimitedError(origin: DiscordParameters) =
        origin
            .ereply(Embeds.error("Currently only Hololive streams are supported for chat relay to offer better support for this feature and limit overuse.\n\nContact kabii if the Hololive channel/video you submitted for relay is incorrectly identified, or if you have a good case for expanding this feature to other streamers."))
            .awaitSingle()

    private suspend fun verifyFeature(origin: DiscordParameters, target: TrackerTarget) {
        val chatTarget = requireNotNull(target as? HoloChatsTarget) { "Invalid target provided to ChatRelayCommand" }
        origin.channelFeatureVerify(chatTarget.channelFeature, chatTarget.featureName, allowOverride = false)
    }

    override suspend fun track(origin: DiscordParameters, target: TargetArguments, features: FeatureChannel?) {
        verifyFeature(origin, target.site)
        // Validate relay request and determine handler to hand off to
        val relay = parseRelay(target.identifier)
        if(relay == null) {
            sendFormatError(origin, target.identifier)
            return
        }

        TrackerCommandBase.sendTrackerTestMessage(origin)

        when(relay) {
            is ChannelRelaySpec -> AutoRelayChannel.track(origin, relay.channelId)
            is VideoRelaySpec -> ManualRelayVideo.track(origin, relay.videoId)
        }
    }

    override suspend fun untrack(origin: DiscordParameters, target: TargetArguments, moveTo: GuildMessageChannel?) {
        verifyFeature(origin, target.site)

        if(moveTo != null) {
            // unlikely to be used, not bothering to implement at this time. no reason this can't be supported, if needed.
            origin.ereply(Embeds.error("Transferring channels is not supported for HoloChat relays.\n\nUse `/untrack` in the old channel and `/track` again in the new channel.")).awaitSingle()
            return
        }

        // Validate untrack request and determine handler to hand off to
        val relay = parseRelay(target.identifier)
        if(relay == null) {
            sendFormatError(origin, target.identifier)
            return
        }
        when(relay) {
            is ChannelRelaySpec -> AutoRelayChannel.untrack(origin, relay.channelId)
            is VideoRelaySpec -> ManualRelayVideo.untrack(origin, relay.videoId)
        }
    }
}