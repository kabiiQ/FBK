package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.anilist.AniListParser
import moe.kabii.discord.trackers.anime.kitsu.KitsuParser
import moe.kabii.discord.trackers.anime.mal.MALParser
import moe.kabii.discord.trackers.anime.watcher.ListServiceChecker
import moe.kabii.discord.trackers.twitter.watcher.TwitterChecker
import moe.kabii.discord.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeFeedPuller
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.discord.trackers.videos.youtube.watcher.YoutubeChecker

data class ServiceRequestCooldownSpec(
    val callDelay: Long,
    val minimumRepeatTime: Long
)

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    private val serviceThreads: List<Thread>
    private var active = false

    init {
        serviceThreads = sequence {
            val reminderDelay = ServiceRequestCooldownSpec(
                callDelay = 0L,
                minimumRepeatTime = 30_000L
            )
            val reminders = ReminderWatcher(discord, reminderDelay)
            yield(Thread(reminders, "ReminderWatcher"))

            val twitchDelay = ServiceRequestCooldownSpec(
                callDelay = 0L,
                minimumRepeatTime = 60_000L
            )
            val twitch = TwitchChecker(discord, twitchDelay)
            yield(Thread(twitch, "TwitchChecker"))

            val subsDelay = ServiceRequestCooldownSpec(
                callDelay = 0L,
                minimumRepeatTime = 12_000L
            )
            val ytSubscriptions = YoutubeSubscriptionManager(discord, subsDelay)
            yield(Thread(ytSubscriptions, "YoutubeSubscriptionManager"))
            
            val ytDelay = ServiceRequestCooldownSpec(
                callDelay = 0L,
                minimumRepeatTime = 30_000L
            )
            val ytChecker = YoutubeChecker(ytSubscriptions, discord, ytDelay)
            yield(Thread(ytChecker, "YoutubeChecker"))

            /*
            val pullDelay = ServiceRequestCooldownSpec(
                callDelay = 5_000L,
                minimumRepeatTime = 120_000L
            )
            val ytManualPuller = YoutubeFeedPuller(pullDelay)
            yield(Thread(ytManualPuller, "YT-ManualFeedPull"))
            */

            val malDelay = ServiceRequestCooldownSpec(
                callDelay = MALParser.callCooldown,
                minimumRepeatTime = 180_000L
            )
            val malChecker = ListServiceChecker(ListSite.MAL, discord, malDelay)
            val malThread = Thread(malChecker, "MediaListWatcher-MAL")
            yield(malThread)

            val kitsuDelay = ServiceRequestCooldownSpec(
                callDelay = KitsuParser.callCooldown,
                minimumRepeatTime = 180_000L
            )
            val kitsuChecker = ListServiceChecker(ListSite.KITSU, discord, kitsuDelay)
            val kitsuThread = Thread(kitsuChecker, "MediaListWatcher-Kitsu")
            yield(kitsuThread)

            val aniListDelay = ServiceRequestCooldownSpec(
                callDelay = AniListParser.callCooldown,
                minimumRepeatTime = 30_000L
            )
            val aniListChecker = ListServiceChecker(ListSite.ANILIST, discord, aniListDelay)
            val aniListThread = Thread(aniListChecker, "MediaListWatcher-AniList")
            yield(aniListThread)

            val twitterDelay = ServiceRequestCooldownSpec(
                callDelay = 3_000L,
                minimumRepeatTime = 30_000L
            )
            val twitter = TwitterChecker(discord, twitterDelay)
            yield(Thread(twitter, "TwitterChecker"))

        }.toList()
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }
}