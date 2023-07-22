package moe.kabii.command.commands.meta

import moe.kabii.command.Command

object TestCommand : Command("test") {
    override val wikiPath: String? = null

    init {
        terminal {
        }

        chat {

        }
    }
}