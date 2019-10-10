package moe.kabii.discord.command.commands.audio

import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Member
import moe.kabii.discord.audio.AudioManager
import moe.kabii.discord.command.Command
import moe.kabii.structure.tryBlock
import moe.kabii.util.tryLock

object BotState : AudioCommandContainer {
    object AudioReset : Command("reset", "restart", "reconnect") {
        init {
            discord {
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val botChannel = event.client.self.flatMap { user -> user.asMember(target.id) }.flatMap(Member::getVoiceState).flatMap(VoiceState::getChannel).tryBlock().orNull()
                tryLock(audio.discord.lock, withLock = {
                    error("Reconnecting the music player for ${target.name}. This should rarely be necessary, I will join when you start to play music or if needed, I can be called with the **summon** command.").block()
                    audio.resetAudio(botChannel)
                }, withoutLock = {
                    error("The music player is currently resetting.").block()
                })
            }
        }
    }

    object BotSummon : Command("summon", "join") {
        init {
            discord {
                validateChannel(this)
                if(!validateVoice(this)) {
                    error("You must be in the bot's voice channel if the bot is in use.").block()
                }
            }
        }
    }
}