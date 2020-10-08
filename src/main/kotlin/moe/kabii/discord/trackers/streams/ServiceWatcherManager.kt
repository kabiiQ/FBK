package moe.kabii.discord.trackers.streams

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.TrackedStreams
import moe.kabii.discord.trackers.streams.twitch.watcher.TwitchChecker

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    // todo utilize streamingsite
    private val streamThreads: Map<TrackedStreams.DBSite, Thread>
    private var active = false

    init {
        val twitch = TwitchChecker(discord)
        val twitchThread = Thread(twitch, "TwitchChecker")

        streamThreads = mapOf(
            TrackedStreams.DBSite.TWITCH to twitchThread
        )
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        active = true
        streamThreads.values.forEach(Thread::start)
    }
}