package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget

object ConfigOverride {
    object Print : Command("printconfig") {
        override val wikiPath: String? = null

        init {
            terminal {
                if(args.size != 2) error("printconfig <bot id> <guild id>")
                val bot = args[0].toInt().run(instances::get)
                val targetGuildId = args[1].toLong()

                val config = GuildConfigurations.guildConfigurations[GuildTarget(bot.clientId, targetGuildId)]
                println(config)
            }
        }
    }

    object Reset : Command("resetconfig") {
        override val wikiPath: String? = null

        init {
            terminal {
                if(args.size != 2) error("resetconfig <bot id> <guild id>")
                val bot = args[0].toInt().run(instances::get)
                val targetGuildId = args[1].toLong()

                val config = GuildConfigurations.guildConfigurations[GuildTarget(bot.clientId, targetGuildId)]
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