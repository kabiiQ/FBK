package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.TempStates

object ToggleTwitter : Command("toggletwitter") {
    override val wikiPath: String? = null

    init {
        terminal {
            if(TempStates.skipTwitter) {
                println("Re-enabling Twitter checker")
                TempStates.skipTwitter = false
            } else {
                println("Disabling Twitter checker")
                TempStates.skipTwitter = true
            }
        }
    }
}