package moe.kabii.command.commands.audio.queue

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.commands.audio.AudioCommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.data.mongodb.guilds.FeatureChannel
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.Embeds

object QueueState : AudioCommandContainer {
    suspend fun pause(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        audio.player.isPaused = true
        ireply(Embeds.fbk("Audio playback is now paused. You can resume playback with the **resume** command.")).awaitSingle()
    }

    suspend fun resume(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        audio.player.isPaused = false
        ireply(Embeds.fbk("Audio playback resumed.")).awaitSingle()
    }

    suspend fun loop(origin: DiscordParameters) = with(origin) {
        // toggles queue "loop" feature
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        if(audio.looping) {
            audio.looping = false
            ireply(Embeds.fbk("Queue loop has been disabled.")).awaitSingle()
        } else {
            audio.looping = true
            ireply(Embeds.fbk("Queue loop has been enabled.")).awaitSingle()
        }
    }
}