package moe.kabii.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.util.Embeds

object QueueLoop : Command("loop") {
    override val wikiPath = "Music-Player#queue-manipulation"

    init {
        discord {
            // toggles queue "loop" feature
            val audio = AudioManager.getGuildAudio(target.id.asLong())
            if(audio.looping) {
                audio.looping = false
                ireply(Embeds.fbk("Queue loop has been disabled.")).awaitSingle()
            } else {
                audio.looping = true
                ireply(Embeds.fbk("Queue loop has been enabled.")).awaitSingle()
            }
        }
    }
}