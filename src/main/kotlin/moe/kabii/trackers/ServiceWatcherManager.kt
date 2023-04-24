package moe.kabii.trackers

import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.anime.anilist.AniListParser
import moe.kabii.trackers.anime.kitsu.KitsuParser
import moe.kabii.trackers.anime.mal.MALParser
import moe.kabii.trackers.anime.watcher.ListServiceChecker
import moe.kabii.trackers.nitter.NitterChecker
import moe.kabii.trackers.mastodon.streaming.MastodonIntake
import moe.kabii.trackers.videos.twitcasting.watcher.TwitcastChecker
import moe.kabii.trackers.videos.twitcasting.webhook.TwitcastWebhookManager
import moe.kabii.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.trackers.videos.twitch.webhook.TwitchSubscriptionManager
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.trackers.videos.youtube.watcher.YoutubeChecker
import moe.kabii.ytchat.YoutubeChatWatcher
import moe.kabii.ytchat.YoutubeMembershipMaintainer

data class ServiceRequestCooldownSpec(
    val callDelay: Long,
    val minimumRepeatTime: Long
)

class ServiceWatcherManager(val discord: DiscordInstances) {
    // launch service watcher threads
    private val serviceThreads: List<Thread>
    private var active = false

    val twitcastChecker: TwitcastChecker
    val twitch: TwitchChecker
//    val spaceChecker: SpaceChecker

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
            minimumRepeatTime = 120_000L
        )
        twitch = TwitchChecker(discord, twitchDelay)
        val twitchSubDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 20_000L
        )
        val twitchSubs = TwitchSubscriptionManager(discord, twitch, twitchSubDelay)

//        val spacesDelay = ServiceRequestCooldownSpec(
//            callDelay = 3_500L,
//            minimumRepeatTime = 60_000L
//        )
//        spaceChecker = SpaceChecker(discord, spacesDelay)

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
            callDelay = 2_000L,
            minimumRepeatTime = 120_000L
        )
        val ytManualPuller = YoutubeFeedPuller(pullDelay)*/

        val malDelay = ServiceRequestCooldownSpec(
            callDelay = MALParser.callCooldown,
            minimumRepeatTime = 30_000L
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

        val nitterDelay = ServiceRequestCooldownSpec(
            callDelay = 1_200L,
            minimumRepeatTime = 120_000L
        )
        val nitterChecker = NitterChecker(discord, nitterDelay)

        val mastodon = MastodonIntake(discord)

        serviceThreads = listOf(
            Thread(reminders, "ReminderWatcher"),
            Thread(twitch, "TwitchChecker"),
            Thread(twitchSubs, "TwitchSubscriptionManager"),
//            Thread(spaceChecker, "TwitterSpacesChecker"),
            Thread(ytSubscriptions, "YoutubeSubscriptionManager"),
            Thread(ytChecker, "YoutubeChecker"),
            //Thread(ytManualPuller, "YT-ManualFeedPull"),
            Thread(ytMembershipMaintainer, "YoutubeMembershipMaintainer"),
            malThread,
            kitsuThread,
            aniListThread,
//            Thread(twitter, "TwitterChecker"),
//            Thread(twitterStream, "TwitterStream"),
            Thread(nitterChecker, "NitterManager"),
            Thread(twitcastChecker, "TwitcastChecker"),
            Thread(TwitcastWebhookManager, "TwitcastWebhookManager"),
            Thread(ytChatWatcher, "YTChatWatcher"),
            Thread(mastodon, "MastodonIntake")
        )
    }

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }
}