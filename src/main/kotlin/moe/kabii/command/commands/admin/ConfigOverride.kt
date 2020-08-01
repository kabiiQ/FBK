package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations

object ConfigOverride {
    object Print : Command("printconfig") {
        override val wikiPath: String? = null

        init {
            terminal {
                val targetGuildId = args.getOrNull(0)?.toLongOrNull()
                requireNotNull(targetGuildId) { "printconfig <guild ID>" }

                val config = GuildConfigurations.guildConfigurations[targetGuildId]
                println(config)
            }
        }
    }

    object Reset : Command("resetconfig") {
        override val wikiPath: String? = null

        init {
            terminal {
                val targetGuildId = args.getOrNull(0)?.toLongOrNull()
                requireNotNull(targetGuildId) { "resetconfig <guild ID>" }

                val config = GuildConfigurations.guildConfigurations[targetGuildId]
                if(config == null) {
                    println("Guild configuration '$targetGuildId' not found.")
                } else {
                    config.removeSelf()
                    println("Guild configuration '$targetGuildId' reset.")
                }
            }
        }
    }
}