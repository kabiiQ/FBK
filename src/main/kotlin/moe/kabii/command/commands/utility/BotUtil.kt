package moe.kabii.command.commands.utility

import discord4j.core.`object`.VoiceState
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds

object BotUtil : CommandContainer {
    object GlitchLink : Command("glitch") {
        override val wikiPath: String? = null // yeah

        init {
            discord {
                val link = "https://discord.com/channels/${target.id.asString()}/${chan.id.asString()}/1"
                ereply(Embeds.fbk("[;)]($link)")).awaitSingle()
            }
        }
    }
}