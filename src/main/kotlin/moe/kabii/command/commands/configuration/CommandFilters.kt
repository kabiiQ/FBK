package moe.kabii.command.commands.configuration

import discord4j.core.`object`.entity.Message
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.discord.util.Search
import reactor.core.publisher.Mono

object  CommandFilters : CommandContainer {
    suspend fun toggleList(param: DiscordParameters, config: GuildConfiguration): Mono<Message> {
        val filter = config.commandFilter
        return if(filter.whitelisted) {
            filter.useBlacklist()
            config.save()
            param.embed("**${param.target.name}** will now use the command blacklist system (default behavior). Use the **blacklist** command to disable other commands.")
        } else {
            filter.useWhitelist()
            config.save()
            param.embed("**${param.target.name}** will now use the command whitelist system. By default most commands will be disabled. See the **whitelist** command to to enable other commands.")
        }
    }

    object Whitelist : Command("whitelist", "white-list") {
        override val wikiPath = "Configuration#using-a-command-blacklist-or-whitelist"
        override val commandExempt = true

        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                if(args.isEmpty()) {
                    usage("**whitelist** is used to set up the bot command whitelist.", "whitelist <add/remove/view/reset/toggle>").awaitSingle()
                    return@discord
                }
                val filter = config.commandFilter
                val match by lazy { args.getOrNull(1)?.let { arg -> Search.commandByAlias(handler, arg) } }
                when(args[0].lowercase()) {
                    "use" -> {
                        if(!filter.whitelisted) {
                            filter.useWhitelist()
                            config.save()
                            embed("**${target.name}** will now use a command **whitelist**. By default most commands will be disabled. See the **whitelist** command to enable other commands.")
                        } else error("Command whitelist is already enabled in **${target.name}**.")
                    }
                    "toggle" -> toggleList(this, config)
                    "add", "insert" -> {
                        if(match == null) {
                            usage("Please specify a bot command to be added to the whitelist using its name.", "whitelist add <command name>")
                        } else {
                            val name = match!!.baseName
                            val add = filter.whitelist.add(name)
                            if(add) {
                                config.save()
                                embed("Command **$name** has been added to the whitelist for **${target.name}**.")
                            } else {
                                error("The command **$name** is already in the whitelist for **${target.name}**.")
                            }
                        }
                    }
                    "remove", "delete" -> {
                        if(match == null) {
                            usage("Please specify a bot command to be removed from the whitelist using its name.", "whitelist remove <command name>")
                        } else {
                            val name = match!!.baseName
                            val remove = filter.whitelist.remove(name)
                            if(remove) {
                                config.save()
                                embed("Command **$name** has been removed from the whitelist for **${target.name}**.")
                            } else {
                                error("The command **$name** is not in the whitelist for **${target.name}**.")
                            }
                        }
                    }
                    "view", "list", "commands" -> {
                        val enabled = if(filter.whitelisted) "**${target.name}** is currently using a command whitelist." else "**${target.name}** is not using a command whitelist."
                        val commands = if(filter.whitelist.isEmpty()) "No commands are on the command whitelist." else "Whitelisted Commands:\n${filter.whitelist.joinToString("\n")}"
                        embed {
                            setDescription("$enabled\n$commands")
                        }
                    }
                    "clear", "reset" -> {
                        filter.whitelist.clear()
                        config.save()
                        embed("The whitelist for **${target.name}** has been reset.")
                    }
                    else -> usage("Unknown task **${args[0]}**.", "whitelist <add/remove/view/reset/toggle>")
                }.awaitSingle()
            }
        }
    }

    object Blacklist : Command("blacklist", "black-list") {
        override val wikiPath = "Configuration#using-a-command-blacklist-or-whitelist"
        override val commandExempt = true

        init {
            discord {
                member.verify(Permission.MANAGE_CHANNELS)
                if(args.isEmpty()) {
                    usage("**blacklist** is used to set up the bot command blacklist.", "blacklist <add/remove/view/reset/toggle>").awaitSingle()
                    return@discord
                }
                val filter = config.commandFilter
                val match by lazy { args.getOrNull(1)?.let { arg -> Search.commandByAlias(handler, arg) } }
                when(args[0].lowercase()) {
                    "use" -> {
                        if(!filter.blacklisted) {
                            filter.useBlacklist()
                            config.save()
                            embed("**${target.name}** will now use a command **blacklist** (default behavior). By default most commands will be enabled. Use the **blacklist** command to disable commands.")
                        } else error("Command blacklist is already enabled in **${target.name}**.")
                    }
                    "toggle" -> toggleList(this, config)
                    "add", "insert" -> {
                        if(match == null) {
                            usage("Please specify a bot command to be added to the blacklist using its name.", "blacklist add <command name>")
                        } else {
                            val name = match!!.baseName
                            val add = filter.blacklist.add(name)
                            if(add) {
                                config.save()
                                embed("Command **$name** has been added to the blacklist for **${target.name}**.")
                            } else {
                                error("The command **$name** is already in the blacklist for **${target.name}**.")
                            }
                        }
                    }
                    "remove", "delete" -> {
                        if(match == null) {
                            usage("Please specify a bot command to be removed from the blacklist using its name.", "blacklist remove <command name>")
                        } else {
                            val name = match!!.baseName
                            val remove = filter.blacklist.remove(name)
                            if(remove) {
                                config.save()
                                embed("Command **$name** has been removed from the blacklist for **${target.name}**.")
                            } else {
                                embed("The command **$name** is not in the blacklist for **${target.name}**.")
                            }
                        }
                    }
                    "view", "list", "commands" -> {
                        val enabled = if(filter.blacklisted) "**${target.name}** is currently using the command blacklist." else "**${target.name}** is not using the command blacklist. (whitelist is active)"
                        val commands = if(filter.blacklist.isEmpty()) "No commands are on the command blacklist." else "Blacklisted Commands:\n${filter.blacklist.joinToString("\n")}"
                        embed {
                            setDescription("$enabled\n$commands")
                        }
                    }
                    "clear", "reset" -> {
                        filter.blacklist.clear()
                        config.save()
                        embed("The blacklist for **${target.name}** has been reset.")
                    }
                    else -> usage("Unknown task **${args[0]}**.", "blacklist <add/remove/view/reset/toggle>")
                }.awaitSingle()
            }
        }
    }
}