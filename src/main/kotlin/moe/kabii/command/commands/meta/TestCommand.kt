package moe.kabii.command.commands.meta

import moe.kabii.command.Command
import moe.kabii.data.mongodb.GuildConfigurations

object MigrationCommand : Command("migration") {
     override val wikiPath: String? = null
    init {
        terminal {
            GuildConfigurations.guildConfigurations.values.forEach { cfg ->
                val add = cfg.selfRoles.reactionRoles
                    .filter { a -> !cfg.autoRoles.reactionConfigurations.contains(a) }
                cfg.autoRoles.reactionConfigurations.addAll(add)
                cfg.save()
            }
        }
    }
}