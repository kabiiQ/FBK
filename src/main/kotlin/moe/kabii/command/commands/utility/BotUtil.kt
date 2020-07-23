package moe.kabii.command.commands.utility

import discord4j.core.`object`.VoiceState
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer

object BotUtil : CommandContainer {
    object Screenshare : Command("screenshare", "screen-share", "share", "sharescreen", "screanshare") {
        override val wikiPath = "Utility-Commands#screenshare"

        init {
            discord {
                member.voiceState
                    .flatMap(VoiceState::getChannel)
                    .flatMap { channel ->
                        val link = "https://discord.com/channels/${target.id.asString()}/${channel.id.asString()}/"
                        embed("[Screenshare channel for **${channel.name}**]($link)")
                    }
                    .switchIfEmpty(embed("You need to be in a voice channel in this guild to use screenshare."))
                    .awaitSingle()
            }
        }
    }

    object GlitchLink : Command("glitch") {
        override val wikiPath: String? = null // yeah

        init {
            discord {
                val link = "https://discord.com/channels/${target.id.asString()}/${chan.id.asString()}/1"
                embed("[;)]($link)").awaitSingle()
            }
        }
    }
}