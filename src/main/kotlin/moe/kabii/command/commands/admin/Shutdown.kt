package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.discord.conversation.Conversation
import kotlin.system.exitProcess

object Shutdown : Command("shutdown", "stop", "end") {
    override val wikiPath: String? = null

    init {
        terminal {
            println("Ending process...")
            val conversations = Conversation.shutdown()
            println("Ended $conversations active conversations.")
            exitProcess(0)
        }
    }
}