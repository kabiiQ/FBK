package moe.kabii.command.commands.meta

import moe.kabii.command.Command
import moe.kabii.util.extensions.propagateTransaction

object MigrationCommand : Command("migration") {
     override val wikiPath: String? = null
    init {
        terminal {

            propagateTransaction {
            }
        }
    }
}