package moe.kabii.trackers

import moe.kabii.LOG
import moe.kabii.data.flat.AvailableServices
import moe.kabii.data.relational.anime.ListSite
import moe.kabii.discord.tasks.ReminderWatcher
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.anime.anilist.AniListParser
import moe.kabii.trackers.anime.kitsu.KitsuParser
import moe.kabii.trackers.anime.mal.MALParser
import moe.kabii.trackers.anime.watcher.ListServiceChecker
import moe.kabii.trackers.posts.bluesky.BlueskyChecker
import moe.kabii.trackers.posts.bluesky.streaming.BlueskyFirehose
import moe.kabii.trackers.posts.twitter.NitterChecker
import moe.kabii.trackers.posts.twitter.SyndicationChecker
import moe.kabii.trackers.videos.kick.watcher.KickChecker
import moe.kabii.trackers.videos.kick.webhook.KickSubscriptionManager
import moe.kabii.trackers.videos.twitcasting.watcher.TwitcastChecker
import moe.kabii.trackers.videos.twitcasting.webhook.TwitcastWebhookManager
import moe.kabii.trackers.videos.twitch.watcher.TwitchChecker
import moe.kabii.trackers.videos.twitch.webhook.TwitchSubscriptionManager
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeFeedPuller
import moe.kabii.trackers.videos.youtube.subscriber.YoutubeSubscriptionManager
import moe.kabii.trackers.videos.youtube.watcher.YoutubeChecker
import moe.kabii.ytchat.YoutubeChatWatcher
import moe.kabii.ytchat.YoutubeMembershipMaintainer

data class ServiceRequestCooldownSpec(
    val callDelay: Long,
    val minimumRepeatTime: Long
)

class ServiceWatcherManager(val discord: DiscordInstances) {
    private val serviceThreads: Sequence<Thread>
    private var active = false

    val twitCastChecker: TwitcastChecker
    val twitch: TwitchChecker
    val kick: KickChecker
    val ytChatWatcher: YoutubeChatWatcher
    val twitterChecker: NitterChecker
    val blueskyFirehose: BlueskyFirehose

    fun launch() {
        check(!active) { "ServiceWatcherManager threads already launched" }
        serviceThreads.forEach(Thread::start)
        active = true
    }

    init {
        //  Initialize all service watchers
        val reminderDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 30_000L
        )
        val reminders = ReminderWatcher(discord, reminderDelay)

        val twitcastCooldowns = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = if(AvailableServices.twitCastingWebhooks) 900_000L else 60_000L
        )
        twitCastChecker = TwitcastChecker(discord, twitcastCooldowns)

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

        ytChatWatcher = YoutubeChatWatcher(discord)

        val ytMembershipMaintainer = YoutubeMembershipMaintainer(discord)

        val pullDelay = ServiceRequestCooldownSpec(
            callDelay = 2_000L,
            minimumRepeatTime = 120_000L
        )
        val ytManualPuller = YoutubeFeedPuller(pullDelay)

        val kickDelay = ServiceRequestCooldownSpec(
            callDelay = 500L,
            minimumRepeatTime = if(AvailableServices.kickWebhooks) 300_000L else 60_000L
        )
        kick = KickChecker(discord, kickDelay)
        val kickSubDelay = ServiceRequestCooldownSpec(
            callDelay = 0L,
            minimumRepeatTime = 20_000L
        )
        val kickSubs = KickSubscriptionManager(discord, kick, kickSubDelay)

        val malDelay = ServiceRequestCooldownSpec(
            callDelay = MALParser.callCooldown,
            minimumRepeatTime = 30_000L
        )
        val malChecker = ListServiceChecker(ListSite.MAL, discord, malDelay)

        val kitsuDelay = ServiceRequestCooldownSpec(
            callDelay = KitsuParser.callCooldown,
            minimumRepeatTime = 180_000L
        )
        val kitsuChecker = ListServiceChecker(ListSite.KITSU, discord, kitsuDelay)

        val aniListDelay = ServiceRequestCooldownSpec(
            callDelay = AniListParser.callCooldown,
            minimumRepeatTime = 30_000L
        )
        val aniListChecker = ListServiceChecker(ListSite.ANILIST, discord, aniListDelay)

        // Twitter (without official API) is complex and will manage its own cooldowns
        twitterChecker = NitterChecker(discord)

        val syndicationDelay = ServiceRequestCooldownSpec(
            callDelay = 2_600L,
            minimumRepeatTime = 60_000L
        )
        val syndicationChecker = SyndicationChecker(discord, syndicationDelay)

        val blueskyDelay = ServiceRequestCooldownSpec(
            callDelay = 600L,
            minimumRepeatTime = 300_000L // TODO increase when confident in streaming implementation
        )
        val blueskyChecker = BlueskyChecker(blueskyDelay, discord)
        blueskyFirehose = BlueskyFirehose(blueskyChecker)

        // Compile the service threads to be enabled
        LOG.info("Starting service initialization")

        suspend fun SequenceScope<Thread>.service(service: Runnable, name: String, condition: Boolean) {
            if(condition) {
                LOG.info("Service $name is enabled")
                yield(Thread(service, name))
            } else {
                LOG.info("Service $name is disabled")
            }
        }

        serviceThreads = sequence {
            service(reminders, "ReminderWatcher", true)
            service(twitch, "TwitchChecker", AvailableServices.twitchApi)
            service(twitchSubs, "TwitchSubscriptionManager", AvailableServices.twitchWebhooks)
            service(kick, "KickChecker", AvailableServices.kickApi)
            service(kickSubs, "KickSubscriptionManager", AvailableServices.kickWebhooks)
            service(ytSubscriptions, "YoutubeSubscriptionManager", AvailableServices.youtubePubSub)
            service(ytChecker, "YoutubeChecker", AvailableServices.youtubeApi)
            service(ytManualPuller, "YT-ManualFeedPull", AvailableServices.youtubePoller)
            service(ytMembershipMaintainer, "YoutubeMembershipMaintainer", true)
            service(malChecker, "MediaListWatcher-MAL", AvailableServices.mal)
            service(kitsuChecker, "MediaListWatcher-Kitsu", true)
            service(aniListChecker, "MediaListWatcher-AniList", AvailableServices.aniList)
            service(twitterChecker, "NitterManager", AvailableServices.nitter)
            // Twitter alternative service using Syndication feeds - may work for a handful of feeds
            service(syndicationChecker, "SyndicationFeedChecker", false)
            service(blueskyChecker, "BlueskyChecker", AvailableServices.bluesky)
            service(blueskyFirehose, "BlueskyFirehoseStream", AvailableServices.bluesky)
            service(twitCastChecker, "TwitcastChecker", AvailableServices.twitCastingApi)
            service(TwitcastWebhookManager, "TwitcastWebhookManager", AvailableServices.twitCastingWebhooks)
            service(ytChatWatcher, "YTChatWatcher", true)
        }
    }
}