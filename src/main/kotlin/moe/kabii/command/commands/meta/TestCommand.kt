package moe.kabii.command.commands.meta

import moe.kabii.command.Command

object TestCommand : Command("testcommand") {
     override val wikiPath: String? = null
    init {
        discord {

        }
    }
}