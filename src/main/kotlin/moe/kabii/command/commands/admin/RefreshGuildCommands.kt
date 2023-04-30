package moe.kabii.command.commands.admin

import moe.kabii.command.Command
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.util.extensions.snowflake

object RefreshGuildCommands : Command("refreshcommands") {
    override val wikiPath: String? = null

    init {
        terminal {

            val targetGuild = args.getOrNull(0)?.toLongOrNull()
            if(targetGuild == null) {
                println("Usage: refreshcommands <guild ID>")
                return@terminal
            }

            val client = instances.getByGuild(targetGuild.snowflake).firstOrNull()
            if(client == null) {
                println("Error getting client for guild ID '$targetGuild'")
                return@terminal
            }

            GuildCommandRegistrar.updateGuildCommands(client, targetGuild)
            println("Guild commands for $targetGuild updated.")
        }
    }
}