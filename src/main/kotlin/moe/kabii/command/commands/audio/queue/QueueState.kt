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
        ireply(Embeds.fbk(i18n("audio_paused"))).awaitSingle()
    }

    suspend fun resume(origin: DiscordParameters) = with(origin) {
        channelFeatureVerify(FeatureChannel::musicChannel)
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        audio.player.isPaused = false
        ireply(Embeds.fbk(i18n("audio_resumed"))).awaitSingle()
    }

    suspend fun loop(origin: DiscordParameters) = with(origin) {
        // toggles queue "loop" feature
        val audio = AudioManager.getGuildAudio(client, target.id.asLong())
        if(audio.looping) {
            audio.looping = false
            ireply(Embeds.fbk(i18n("audio_queue_loop_off"))).awaitSingle()
        } else {
            audio.looping = true
            ireply(Embeds.fbk(i18n("audio_queue_loop_on"))).awaitSingle()
        }
    }
}