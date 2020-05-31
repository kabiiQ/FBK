package moe.kabii.discord.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify

object CommandOptions : CommandContainer {
    object Prefix : Command("prefix", "setprefix", "prefix-set", "set-prefix", "changeprefix") {
        override val commandExempt = true
        init {
            discord {
                if(args.isEmpty()) {
                    // display current prefix
                    val prefix = config.prefix
                    embed("The current command prefix for **${target.name}** is **$prefix**. Command example for changing prefix: **prefix $prefix**.").awaitSingle()
                    return@discord
                }
                member.verify(Permission.MANAGE_GUILD)
                config.prefix = args[0]
                config.save()
                embed("Command prefix for **${target.name}** has been set to **${args[0]}** Commands are also accessible using the global bot prefix (;;)").awaitSingle()
            }
        }
    }

    object Suffix : Command("suffix", "setsuffix", "set-suffix", "changesuffix") {
        override val commandExempt = true
        init {
            discord {
                if(args.isEmpty()) {
                    val suffix = config.suffix
                    embed("The current command prefix for **${target.name}** is **$suffix**. Command example for changing suffix: **suffix desu**. The suffix can be removed with **suffix remove**.").awaitSingle()
                    return@discord
                }
                member.verify(Permission.MANAGE_GUILD)
                val suffix = when(args[0]) {
                    "none", "remove", "reset" -> null
                    else -> args[0]
                }
                config.suffix = suffix
                config.save()
                embed("The command suffix for **${target.name}** has been set to **$suffix**.").awaitSingle()
            }
        }
    }
}