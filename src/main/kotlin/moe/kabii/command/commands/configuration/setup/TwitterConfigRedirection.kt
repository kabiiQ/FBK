package moe.kabii.command.commands.configuration.setup

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds

object TwitterConfigRedirection : Command("twitter") {
    override val wikiPath: String? = null

    init {
        chat {
            ereply(Embeds.fbk("`/twitter` options have moved to `/posts`!")).awaitSingle()
        }
    }
}