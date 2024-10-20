package moe.kabii.command.commands.utility

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.discord.util.Embeds

object BotUtil : CommandContainer {
    object GlitchLink : Command("top") {
        override val wikiPath = "Utility-Commands#jump-to-start-of-discord-channel"

        init {
            chat {
                val link = "https://discord.com/channels/${target.id.asString()}/${chan.id.asString()}/1"
                ereply(Embeds.fbk("[Jump to top of channel]($link)")).awaitSingle()
            }
        }
    }
}