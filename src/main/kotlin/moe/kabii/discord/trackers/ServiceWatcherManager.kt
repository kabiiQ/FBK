package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.anilist.AniListParser
import moe.kabii.discord.trackers.anime.watcher.ListServiceChecker
import moe.kabii.discord.trackers.anime.watcher.MediaListCooldownSpec
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

            val malDelay = MediaListCooldownSpec(
                listDelay = 3500L,
                minimumRepeatTime = 250_000L
            )
            val malChecker = ListServiceChecker(ListSite.MAL, discord, malDelay)
            val malThread = Thread(malChecker, "MediaListWatcher-MAL")
            yield(malThread)

            val kitsuDelay = MediaListCooldownSpec(
                listDelay = 2000L,
                minimumRepeatTime = 180_000L
            )
            val kitsuChecker = ListServiceChecker(ListSite.KITSU, discord, kitsuDelay)
            val kitsuThread = Thread(kitsuChecker, "MediaListWatcher-Kitsu")
            yield(kitsuThread)

            val aniListDelay = MediaListCooldownSpec(
                listDelay = AniListParser.callCooldown,
                minimumRepeatTime = 30_000L
            )
            val aniListChecker = ListServiceChecker(ListSite.ANILIST, discord, aniListDelay)
            val aniListThread = Thread(aniListChecker, "MediaListWatcher-AniList")
            yield(aniListThread)

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