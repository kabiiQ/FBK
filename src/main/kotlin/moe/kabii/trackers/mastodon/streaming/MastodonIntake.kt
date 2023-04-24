package moe.kabii.trackers.mastodon.streaming

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import moe.kabii.discord.tasks.DiscordTaskPool
import moe.kabii.instances.DiscordInstances
import moe.kabii.trackers.mastodon.json.MastodonStatus

class MastodonIntake(val instances: DiscordInstances) : Runnable {
    internal data class Status(
        val dbFeed: Int,
        val status: MastodonStatus
    )

    private val queue = Channel<Status>(Channel.UNLIMITED)

    fun intakeStatus(dbFeed: Int, status: MastodonStatus) {
        queue.trySend(Status(dbFeed, status))
    }

    override fun run() {
        /*
        TODO implement
        get db feed, associated targets from db
        send notifications to discord channels
        use intakeScope
         */
    }
}