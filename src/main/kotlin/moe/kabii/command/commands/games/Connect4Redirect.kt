package moe.kabii.command.commands.games

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds

object Connect4Redirect : Command("connect4") {
    override val wikiPath: String? = null

    init {
        chat {
            ereply(Embeds.error("The /connect4 command has moved! See **/game connect4**.")).awaitSingle()
        }
    }
}