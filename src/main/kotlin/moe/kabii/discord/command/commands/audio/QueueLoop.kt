package moe.kabii.discord.command.commands.audio

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command

object QueueLoop : Command("loop") {
    init {
        discord {
            // toggles queue "loop" feature
            channelVerify(Permission.MANAGE_MESSAGES)
            val audio = AudioManager.getGuildAudio(target.id.asLong())
            if(audio.looping) {
                audio.looping = false
                embed("Queue loop has been disabled.").awaitSingle()
            } else {
                audio.looping = true
                embed("Queue loop has been enabled.").awaitSingle()
            }
        }
    }
}