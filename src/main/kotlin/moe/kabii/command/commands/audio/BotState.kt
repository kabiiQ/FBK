package moe.kabii.command.commands.audio

import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.audio.AudioManager
import moe.kabii.structure.extensions.tryAwait

object BotState : AudioCommandContainer {
    object AudioReset : Command("restart", "reconnect") {
        override val wikiPath: String? = null // intentionally undocumented command

        init {
            discord {
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val botChannel = audio.discord.connection?.channelId?.awaitFirstOrNull()
                    ?.run(target::getChannelById)
                    ?.ofType(VoiceChannel::class.java)
                    ?.tryAwait()?.orNull()
                audio.refreshAudio(botChannel)
            }
        }
    }

    object BotSummon : Command("summon", "join") {
        override val wikiPath = "Music-Player#playing-audio"

        init {
            discord {
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    error(voice.error).awaitSingle()
                }
            }
        }
    }
}