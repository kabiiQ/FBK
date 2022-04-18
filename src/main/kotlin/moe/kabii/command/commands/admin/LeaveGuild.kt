package moe.kabii.command.commands.admin

import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.snowflake

object LeaveGuild : Command("leaveguild") {
    override val wikiPath: String? = null

    init {
        terminal {
            val guildId = args[0].toLong()
            val guild = discord.getGuildById(guildId.snowflake).awaitSingle()
            val guildName = guild.name
            guild.leave().awaitAction()
            println("Leaving guild: ${guildName} (${guild.id.asString()}")
        }
    }
}