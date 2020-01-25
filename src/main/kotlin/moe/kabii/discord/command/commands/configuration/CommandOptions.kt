package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify

object CommandOptions : CommandContainer {
    object Prefix : Command("prefix", "setprefix", "prefix-set", "set-prefix", "changeprefix") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                if(args.isEmpty()) {
                    usage("Sets the command prefix for **${target.name}**.", "prefix !").awaitSingle()
                    return@discord
                }
                config.prefix = args[0]
                config.save()
                embed("Command prefix for **${target.name}** has been set to **${args[0]}** Commands are also accessible using the global bot prefix (;;)").awaitSingle()
            }
        }
    }

    object Suffix : Command("suffix", "setsuffix", "set-suffix", "changesuffix") {
        init {
            discord {
                member.verify(Permission.MANAGE_GUILD)
                if(args.isEmpty()) {
                    usage("Sets the command suffix for **${target.name}**. The suffix can be removed with **suffix none**.", "suffix desu").awaitSingle()
                    return@discord
                }
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