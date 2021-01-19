package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.watcher.ListServiceChecker
import moe.kabii.discord.trackers.twitter.watcher.TwitterChecker
import moe.kabii.discord.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.discord.trackers.videos.youtube.watcher.YoutubeChecker

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    private val serviceThreads: List<Thread>
    private var active = false

    init {
        serviceThreads = sequence {
            val reminders = ReminderWatcher(discord)
            yield(Thread(reminders, "ReminderWatcher"))

            val twitch = TwitchChecker(discord)
            yield(Thread(twitch, "TwitchChecker"))

            val ytSubscriptions = YoutubeSubscriptionManager(discord)
            yield(Thread(ytSubscriptions, "YoutubeSubscriptionManager"))

            val ytChecker = YoutubeChecker(ytSubscriptions, discord)
            yield(Thread(ytChecker, "YoutubeChecker"))

            val mediaThreads = ListSite.values().map { site ->
                // one thread per serivce, we never want to make simultaneous requests due to heavy rate limits to these services
                val checker = ListServiceChecker(site, discord)
                Thread(checker, "ListWatcher-${site.name}")
            }
            yieldAll(mediaThreads)

            val twitter = TwitterChecker(discord)
            yield(Thread(twitter, "TwitterChecker"))

        }.toList()
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }
}