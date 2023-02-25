package moe.kabii.command.commands.admin

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import moe.kabii.command.Command
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.snowflake

object LeaveGuild : Command("leaveguild") {
    override val wikiPath: String? = null

    init {
        terminal {
            // owner use only: forcibly leave a bot instance from a specific guild
            if(args.size != 2) {
                println("leaveguild <instance #> <guild id>")
                return@terminal
            }
            val instanceId = args[0].toInt()
            val guildId = args[1].toLong()
            val bot = instances.check(instanceId) ?: return@terminal
            val guild = bot.client.getGuildById(guildId.snowflake).awaitSingle()
            guild.leave().awaitAction()
            println("Leaving guild: ${guild.name} (${guild.id.asString()})")
        }
    }
}

object GetGuild : Command("getguild") {
    override val wikiPath: String? = null

    init {
        terminal {
            // owner use only: verify current membership of bot instances to specific guilds
            if(args.size != 1) {
                println("getguild <guild id>")
                return@terminal
            }

            val guildId = args[0].toLong()
            val memberInstances = instances.all()
                .associateWith { fbk ->
                    // check if each instance is part of the target guild
                    fbk.client
                        .getGuildById(guildId.snowflake)
                        .awaitSingleOrNull()
                }
                .filterValues { guild -> guild != null }
                .entries

            val instance = memberInstances.firstOrNull()
            if(instance != null) {
                // if ANY instance is member of guild, print basic info about guild
                val (fbk, guild) = instance
                println("Discord guild ${guild!!.id.asString()}: ${guild.name} (${guild.memberCount} members)")

                // then display each joined instance's info. would not need to be in conditional
                memberInstances.forEach { (fbk, iGuild) ->
                    println("FBK instance #${fbk.clientId}: joined ${iGuild!!.joinTime}")
                }
            }
        }
    }
}