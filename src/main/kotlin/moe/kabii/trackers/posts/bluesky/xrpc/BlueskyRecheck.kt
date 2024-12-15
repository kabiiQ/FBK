package moe.kabii.trackers.posts.bluesky.xrpc

import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import moe.kabii.discord.tasks.DiscordTaskPool
import java.time.Duration

/**
 * Recheck system to reduce missed updates due to cached feeds without performing duplicate API calls
 */
object BlueskyRecheck {
    private val recheckDelay = Duration.ofSeconds(15)
    private val recheckScope = CoroutineScope(DiscordTaskPool.socialThreads + CoroutineName("Bluesky-Rechecker") + SupervisorJob())

    /**
     * Pending update jobs by did
     */
    private val pendingUpdates = mutableMapOf<String, Job>()

    fun isPending(did: String) = pendingUpdates.contains(did)

    private fun cancelPending(did: String) = pendingUpdates.remove(did)?.cancel()

    fun scheduleUpdate(did: String, action: suspend (String) -> Unit) {
        cancelPending(did) // "push back" a scheduled update if another update is occuring
        val job = recheckScope.launch {
            delay(recheckDelay)
            pendingUpdates.remove(did)
            action(did)
        }
        pendingUpdates[did] = job
    }
}