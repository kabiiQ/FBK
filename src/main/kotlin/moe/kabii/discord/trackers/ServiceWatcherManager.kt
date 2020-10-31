package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.trackers.anime.watcher.ListServiceChecker
import moe.kabii.discord.trackers.streams.twitch.watcher.TwitchChecker

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    private val serviceThreads: List<Thread>
    private var active = false

    init {
        serviceThreads = sequence {
            val twitch = TwitchChecker(discord)
            yield(Thread(twitch, "TwitchChecker"))

            // todo youtube process

            val mediaThreads = ListSite.values().map { site ->
                // one thread per serivce, we never want to make simultaneous requests due to heavy rate limits to these services
                val checker = ListServiceChecker(site, discord)
                Thread(checker, "ListWatcher-${site.name}")
            }
            yieldAll(mediaThreads)

        }.toList()
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }
}