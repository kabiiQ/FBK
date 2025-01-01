package moe.kabii.command.commands.ytchat

import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.discord.util.Embeds

object ManualRelayChat : Command("relaychat") {
    override val wikiPath: String? = null

    init {
        chat {
            // TODO remove redirection command once users discover
            // Also remove this command being sent to servers
            ereply(Embeds.fbk("/relaychat has moved! Use `/track` with the YouTube video ID, ensuring to select `site: HoloChats`.")).awaitSingle()
        }
    }
}