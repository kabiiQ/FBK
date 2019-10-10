package moe.kabii.discord.event.guild

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.event.domain.guild.GuildUpdateEvent
import moe.kabii.discord.audio.AudioManager
import moe.kabii.util.lock

object GuildUpdateHandler {
    fun handle(event: GuildUpdateEvent) {
        val old = event.old.get()
        val guildID = event.current.id.asLong()
        val botID = event.client.selfId.get()
        if(old.regionId != event.current.regionId) {
            // if we are connected to a voice channel then refresh the voice connection on region change or everything will break
            val audio = AudioManager.guilds[guildID]
            if(audio != null) {
                event.current.voiceStates
                    .filter { state -> state.userId == botID }
                    .flatMap(VoiceState::getChannel)
                    .ofType(VoiceChannel::class.java)
                    .map { voice ->
                        lock(audio.discord.lock) {
                            audio.resetAudio(voice)
                        }
                    }
                    .subscribe()
            }
        }
    }
}