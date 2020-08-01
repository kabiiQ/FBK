package moe.kabii.discord.audio

import discord4j.voice.VoiceConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.kabii.LOG
import moe.kabii.structure.extensions.success

data class AudioConnection(
    val guild: GuildAudio,
    var connection: VoiceConnection? = null,
    val mutex: Mutex = Mutex(),
    var timeoutJob: Job? = null
) {
    fun cancelPendingTimeout() {
        if(timeoutJob != null) {
            checkNotNull(timeoutJob).cancel()
            timeoutJob = null
        }
    }

    suspend fun startTimeout() {
        // already scheduled, push back the disconnection
        cancelPendingTimeout()

        val newJob = with(guild.manager.timeouts) {
            timeoutContext.launch {
                delay(Timeouts.TIMEOUT_DELAY)

                if(this.isActive) {
                    LOG.debug("Timeout executing for guild ${guild.guildId}")
                    mutex.withLock {
                        connection?.disconnect()?.success()?.awaitSingle()
                        timeoutJob = null
                    }
                }
            }
        }
        timeoutJob = newJob
    }
}