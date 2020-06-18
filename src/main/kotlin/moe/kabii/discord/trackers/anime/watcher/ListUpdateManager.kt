package moe.kabii.discord.trackers.anime.watcher

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.mongodb.MediaSite

class ListUpdateManager(val discord: GatewayDiscordClient) {
    private val listThreads: MutableMap<MediaSite, ListServiceChecker> = mutableMapOf()

    fun launch() {
        check(listThreads.isEmpty()) { "ListUpdateManager threads already launched" }
        for(site in MediaSite.values()) {
            // one thread per service, we never want to make simultaneous requests due to heavy rate limits.
            val checker = ListServiceChecker(this, site, discord)
            val thread = Thread(checker, "ListWatcher-${site.name}")
            thread.start()
            listThreads[site] = checker
        }
    }
}