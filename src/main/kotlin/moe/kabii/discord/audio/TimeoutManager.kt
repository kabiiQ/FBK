package moe.kabii.discord.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.structure.extensions.success
import java.util.concurrent.Executors

class TimeoutManager {
    companion object {
        const val TIMEOUT_DELAY = 120_000L
    }

    private val timeoutThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val timeoutContext = CoroutineScope(timeoutThread + CoroutineName("Voice-Timeout") + SupervisorJob())

    private val timeoutJobs: MutableMap<GuildAudio, Job> = mutableMapOf()

    fun cancelPendingTimeout(guildAudio: GuildAudio) {
        val match = timeoutJobs.filterKeys { audio -> audio.guild == guildAudio.guild }.entries.firstOrNull()
        if(match != null) {
            match.value.cancel()
            timeoutJobs.remove(match.key)
        }
    }

    suspend fun startTimeout(guildAudio: GuildAudio) {
        // already scheduled, push back
        cancelPendingTimeout(guildAudio)

        val timeoutJob = timeoutContext.launch {
            delay(TIMEOUT_DELAY) // delay before actually leaving

            if(isActive) {
                println("Timeout executing for guild ${guildAudio.discord}")
                guildAudio.discord.connection?.disconnect()?.success()?.awaitSingle()
                timeoutJobs.remove(guildAudio)
            }
        }
        timeoutJobs[guildAudio] = timeoutJob
    }
}