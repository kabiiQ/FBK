package moe.kabii.discord.trackers.streams

import discord4j.core.GatewayDiscordClient
import moe.kabii.discord.trackers.streams.twitch.watcher.TwitchChecker
import moe.kabii.discord.trackers.streams.youtube.watcher.YoutubeLiveScraper

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    private val streamThreads: List<Thread>
    private var active = false

    init {
        streamThreads = sequence {
            val twitch = TwitchChecker(discord)
            yield(Thread(twitch, "TwitchChecker"))

            val youtubeChecker = YoutubeLiveScraper(discord)
            yield(Thread(youtubeChecker, "YoutubeChannelChecker"))
        }.toList()
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        streamThreads.forEach(Thread::start)
        active = true
    }
}