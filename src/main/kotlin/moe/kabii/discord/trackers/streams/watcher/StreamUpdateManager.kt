package moe.kabii.discord.trackers.streams.watcher

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.TrackedStreams

class StreamUpdateManager(val discord: GatewayDiscordClient) {
    private val streamThreads: MutableMap<TrackedStreams.Site, StreamServiceChecker> = mutableMapOf()

    fun launch() {
        check(streamThreads.isEmpty()) { "StreamUpdateManager threads already launched" }
        for(site in TrackedStreams.Site.values()) {
            val checker = StreamServiceChecker(this, site, discord)
            val thread = Thread(checker, "StreamWatcher-${site.name}")
            thread.start()
            streamThreads[site] = checker
        }
    }

}