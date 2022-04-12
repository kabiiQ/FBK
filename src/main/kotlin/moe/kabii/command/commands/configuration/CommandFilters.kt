package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds

object CommandFilters : CommandContainer {
    object Whitelist : Command("whitelist") {
        override val wikiPath = "Configuration#using-a-command-blacklist-or-whitelist"
        override val commandExempt = true

        init {
            chat {
                member.verify(Permission.MANAGE_CHANNELS)
                val filter = config.commandFilter
                val args = subArgs(subCommand)
                val match by lazy { handler.searchCommandByName(args.string("command")) }
                when(subCommand.name) {
                    "use" -> {
                        if(!filter.whitelisted) {
                            filter.useWhitelist()
                            config.save()
                            ireply(Embeds.fbk("**${target.name}** will now use a command **whitelist**. By default most commands will be disabled. See the **whitelist** command to enable other commands."))
                        } else ereply(Embeds.error("Command whitelist is already enabled in **${target.name}**."))
                    }
                    "add" -> {
                        if(match == null) {
                            ereply(Embeds.error("Please specify a bot command to be added to the whitelist using its name."))
                        } else {
                            val name = match!!.name
                            val add = filter.whitelist.add(name)
                            if(add) {
                                config.save()
                                ireply(Embeds.fbk("Command **/$name** has been added to the whitelist for **${target.name}**."))
                            } else {
                                error("The command **/$name** is already in the whitelist for **${target.name}**.")
                            }
                        }
                    }
                    "remove" -> {
                        if(match == null) {
                            ereply(Embeds.error("Please specify a bot command to be removed from the whitelist using its name."))
                        } else {
                            val name = match!!.name
                            val remove = filter.whitelist.remove(name)
                            if(remove) {
                                config.save()
                                ireply(Embeds.fbk("Command **$name** has been removed from the whitelist for **${target.name}**."))
                            } else {
                                ereply(Embeds.error("The command **$name** is not in the whitelist for **${target.name}**."))
                            }
                        }
                    }
                    "view" -> {
                        val enabled = if(filter.whitelisted) "**${target.name}** is currently using a command whitelist." else "**${target.name}** is not using a command whitelist."
                        val commands = if(filter.whitelist.isEmpty()) "No commands are on the command whitelist." else "Whitelisted Commands:\n${filter.whitelist.joinToString("\n")}"
                        ireply(Embeds.fbk("$enabled\n$commands"))
                    }
                    "clear" -> {
                        filter.whitelist.clear()
                        config.save()
                        ireply(Embeds.fbk("The whitelist for **${target.name}** has been reset."))
                    }
                    else -> error("subcommand mismatch")
                }.awaitSingle()
            }
        }
    }

    object Blacklist : Command("blacklist") {
        override val wikiPath = "Configuration#using-a-command-blacklist-or-whitelist"
        override val commandExempt = true

        init {
            chat {
                member.verify(Permission.MANAGE_CHANNELS)
                val filter = config.commandFilter
                val args = subArgs(subCommand)
                val match by lazy { handler.searchCommandByName(args.string("command")) }
                when(subCommand.name) {
                    "use" -> {
                        if(!filter.blacklisted) {
                            filter.useBlacklist()
                            config.save()
                            ireply(Embeds.fbk("**${target.name}** will now use a command **blacklist** (default behavior). By default most commands will be enabled. Use the **blacklist** command to disable commands."))
                        } else ereply(Embeds.error("Command blacklist is already enabled in **${target.name}**."))
                    }
                    "add" -> {
                        if(match == null) {
                            ereply(Embeds.error("Please specify a bot command to be added to the blacklist using its name."))
                        } else {
                            val name = match!!.name
                            val add = filter.blacklist.add(name)
                            if(add) {
                                config.save()
                                ireply(Embeds.fbk("Command **$name** has been added to the blacklist for **${target.name}**."))
                            } else {
                                ereply(Embeds.error("The command **$name** is already in the blacklist for **${target.name}**."))
                            }
                        }
                    }
                    "remove" -> {
                        if(match == null) {
                            ereply(Embeds.error("Please specify a bot command to be removed from the blacklist using its name."))
                        } else {
                            val name = match!!.name
                            val remove = filter.blacklist.remove(name)
                            if(remove) {
                                config.save()
                                ireply(Embeds.fbk("Command **$name** has been removed from the blacklist for **${target.name}**."))
                            } else {
                                ereply(Embeds.error("The command **$name** is not in the blacklist for **${target.name}**."))
                            }
                        }
                    }
                    "view" -> {
                        val enabled = if(filter.blacklisted) "**${target.name}** is currently using the command blacklist." else "**${target.name}** is not using the command blacklist. (whitelist is active)"
                        val commands = if(filter.blacklist.isEmpty()) "No commands are on the command blacklist." else "Blacklisted Commands:\n${filter.blacklist.joinToString("\n")}"
                        ireply(Embeds.fbk("$enabled\n$commands"))
                    }
                    "clear" -> {
                        filter.blacklist.clear()
                        config.save()
                        ireply(Embeds.fbk("The blacklist for **${target.name}** has been reset."))
                    }
                    else -> error("subcommand mismatch")
                }.awaitSingle()
            }
        }
    }
}