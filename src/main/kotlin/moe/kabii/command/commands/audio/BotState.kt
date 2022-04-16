package moe.kabii.command.commands.audio

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.awaitAction

object BotState : AudioCommandContainer {
    object BotSummon : Command("join") {
        override val wikiPath = "Music-Player#Music-Player#commands"

        init {
            chat {
                event.deferReply().awaitAction()
                val voice = AudioStateUtil.checkAndJoinVoice(this)
                if(voice is AudioStateUtil.VoiceValidation.Failure) {
                    event.editReply()
                        .withEmbeds(Embeds.error(voice.error))
                        .awaitSingle()
                } else {
                    event.deleteReply().awaitAction()
                }
            }
        }
    }
}