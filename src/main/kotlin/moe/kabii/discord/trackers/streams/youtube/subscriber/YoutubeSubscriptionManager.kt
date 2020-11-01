package moe.kabii.discord.trackers.streams.youtube.subscriber

import discord4j.core.GatewayDiscordClient
import moe.kabii.discord.trackers.streams.StreamWatcher

class YoutubeSubscriptionManager(discord: GatewayDiscordClient) : Runnable, StreamWatcher(discord) {

    private val subscriber = YoutubeFeedSubscriber()
    private val listener = YoutubeFeedListener(this)

    var currentSubscriptions = listOf<String>()

    override fun run() {
        // start callback server
        listener.server.start()

        // subscribe to updates for all channels with active targets

        // unsubscribe to entries in currentSubscriptions without active targets

        // save currentsubscriptions state
    }

}