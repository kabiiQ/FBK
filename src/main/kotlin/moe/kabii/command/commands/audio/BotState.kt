package moe.kabii.command.commands.audio

import discord4j.core.`object`.entity.channel.VoiceChannel
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.audio.AudioManager
import moe.kabii.command.Command
import moe.kabii.structure.tryAwait

object BotState : AudioCommandContainer {
    object AudioReset : Command("restart", "reconnect") {
        override val wikiPath: String? = null // intentionally undocumented command

        init {
            discord {
                val audio = AudioManager.getGuildAudio(target.id.asLong())
                val connection = audio.discord.connection
                if(connection != null && connection.isConnected.awaitFirst()) {
                    // should already be connected. reaffirm
                    val botChannel = connection.channelId.awaitFirstOrNull()?.run(target::getChannelById)?.ofType(VoiceChannel::class.java)?.tryAwait()?.orNull()
                    val join = if(botChannel != null) {
                        audio.joinChannel(botChannel)
                    } else null
                    if(join?.orNull() == null) {
                        error("Unable to connect. Try to play audio in a channel you are sure I have permissions to join.").awaitFirst()
                        return@discord
                    }
                }
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