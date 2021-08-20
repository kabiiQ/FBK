package moe.kabii.discord.trackers

import discord4j.core.GatewayDiscordClient
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.discord.trackers.anime.anilist.AniListParser
import moe.kabii.discord.trackers.anime.kitsu.KitsuParser
import moe.kabii.discord.trackers.anime.mal.MALParser
import moe.kabii.discord.trackers.anime.watcher.ListServiceChecker
import moe.kabii.discord.trackers.ps2.wss.PS2EventStream
import moe.kabii.discord.trackers.twitter.watcher.TweetStream
import moe.kabii.discord.trackers.twitter.watcher.TwitterChecker
import moe.kabii.discord.trackers.videos.twitcasting.watcher.TwitcastChecker
import moe.kabii.discord.trackers.videos.twitcasting.webhook.TwitcastWebhookManager
import moe.kabii.discord.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.discord.trackers.videos.twitch.webhook.TwitchFeedSubscriber
import moe.kabii.discord.trackers.videos.twitch.webhook.TwitchSubscriptionManager
import moe.kabii.discord.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.discord.trackers.videos.youtube.watcher.YoutubeChecker
import moe.kabii.discord.ytchat.YoutubeChatWatcher
import moe.kabii.discord.ytchat.YoutubeMembershipMaintainer

data class ServiceRequestCooldownSpec(
    val callDelay: Long,
    val minimumRepeatTime: Long
)

class ServiceWatcherManager(val discord: GatewayDiscordClient) {
    // launch service watcher threads
    private val serviceThreads: List<Thread>
    private var active = false

    val twitcastChecker: TwitcastChecker
    val twitch: TwitchChecker
    val twitchFeedSub: TwitchFeedSubscriber

    init {
        val reminderDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 30_000L
        )
        val reminders = ReminderWatcher(discord, reminderDelay)

        val twitcastCooldowns = ServiceRequestCooldownSpec(
            callDelay = 1100L,
            minimumRepeatTime = 900_000L
        )
        twitcastChecker = TwitcastChecker(discord, twitcastCooldowns)

        val twitchDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 900_000L
        )
        twitch = TwitchChecker(discord, twitchDelay)
        val twitchSubDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 12_000L
        )
        val twitchSubs = TwitchSubscriptionManager(discord, twitch, twitchSubDelay)
        twitchFeedSub = twitchSubs.subscriber

        val subsDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 12_000L
        )
        val ytSubscriptions = YoutubeSubscriptionManager(discord, subsDelay)
        val ytDelay = ServiceRequestCooldownSpec(
            callDelay = 15_000L,
            minimumRepeatTime = 30_000L
        )
        val ytChecker = YoutubeChecker(ytSubscriptions, ytDelay)
        ytSubscriptions.checker = ytChecker

        val ytChatWatcher = YoutubeChatWatcher(discord)

        val ytMembershipMaintainer = YoutubeMembershipMaintainer(discord)

        /*val pullDelay = ServiceRequestCooldownSpec(
            callDelay = 5_000L,
            minimumRepeatTime = 120_000L
        )
        val ytManualPuller = YoutubeFeedPuller(pullDelay)*/


        val malDelay = ServiceRequestCooldownSpec(
            callDelay = MALParser.callCooldown,
            minimumRepeatTime = 180_000L
        )
        val malChecker = ListServiceChecker(ListSite.MAL, discord, malDelay)
        val malThread = Thread(malChecker, "MediaListWatcher-MAL")

        val kitsuDelay = ServiceRequestCooldownSpec(
            callDelay = KitsuParser.callCooldown,
            minimumRepeatTime = 180_000L
        )
        val kitsuChecker = ListServiceChecker(ListSite.KITSU, discord, kitsuDelay)
        val kitsuThread = Thread(kitsuChecker, "MediaListWatcher-Kitsu")

        val aniListDelay = ServiceRequestCooldownSpec(
            callDelay = AniListParser.callCooldown,
            minimumRepeatTime = 30_000L
        )
        val aniListChecker = ListServiceChecker(ListSite.ANILIST, discord, aniListDelay)
        val aniListThread = Thread(aniListChecker, "MediaListWatcher-AniList")

        val twitterDelay = ServiceRequestCooldownSpec(
            callDelay = 3_000L,
            minimumRepeatTime = 30_000L
        )
        val twitter = TwitterChecker(discord, twitterDelay)
        val twitterStream = TweetStream(twitter)

        val ps2Websocket = PS2EventStream(discord)

        serviceThreads = listOf(
            Thread(reminders, "ReminderWatcher"),
            Thread(twitch, "TwitchChecker"),
            Thread(twitchSubs, "TwitchSubscriptionManager"),
            Thread(ytSubscriptions, "YoutubeSubscriptionManager"),
            Thread(ytChecker, "YoutubeChecker"),
            //Thread(ytManualPuller, "YT-ManualFeedPull"),
            Thread(ytMembershipMaintainer, "YoutubeMembershipMaintainer"),
            malThread,
            kitsuThread,
            aniListThread,
            Thread(twitter, "TwitterChecker"),
            Thread(twitterStream, "TwitterStream"),
            Thread(ps2Websocket, "PS2EventStream"),
            Thread(twitcastChecker, "TwitcastChecker"),
            Thread(TwitcastWebhookManager, "TwitcastWebhookManager"),
            Thread(ytChatWatcher, "YTChatWatcher")
        )
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }
}