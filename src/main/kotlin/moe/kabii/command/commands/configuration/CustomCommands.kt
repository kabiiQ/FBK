package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.guilds.CustomCommand
import moe.kabii.discord.util.Embeds

object CustomCommands : CommandContainer {
    private suspend fun addCommand(config: GuildConfiguration, args: List<String>, noCmd: String, restrict: Boolean = false): String {
        val command = args[0].lowercase()
        val response = noCmd.substring(command.length + 1)
        val custom = CustomCommand(command, response, restrict)

        val restricted = if (restrict) "Restricted c" else "C"
        val reply =
                if (config.customCommands.insertIsUpdated(custom))
                    "${restricted}ommand \"$command\" has been updated."
                else "${restricted}ommand \"$command\" has been added."
        config.save()
        return reply
    }

    object Add : Command("addcommand", "add-command", "command-add", "commandadd", "newcommand", "editcommand", "command-edit", "edit-command") {
        override val wikiPath = "Custom-Commands#creating-a-command-with-addcommand"

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.size >= 2) {
                    val add = addCommand(config, args, noCmd)
                    reply(Embeds.fbk(add)).awaitSingle()
                } else {
                    usage("Add or edit a text command. Example:", "addcommand yt My channel: https://youtube.com/mychannel").awaitSingle()
                }
            }
        }
    }

    object Mod : Command("modcommand", "mod-command", "command-mod", "commandmod", "editmodcommand") {
        override val wikiPath: String? = null

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.size >= 2) {
                    val add = addCommand(config, args, noCmd, restrict = true)
                    reply(Embeds.fbk(add)).awaitSingle()
                } else {
                    usage("Add a moderator-only command. Example:", "modcommand yt My channel: https://youtube.com/mychannel").awaitSingle()
                }
            }
        }
    }

    object Remove : Command("removecommand", "delcommand", "remcommand", "deletecommand", "remove-command") {
        override val wikiPath = "Custom-Commands#removing-a-command-with-removecommand"

        private suspend fun removeCommand(config: GuildConfiguration, command: String): String {
            val reply =
                    if (config.customCommands.removeByName(command))
                        "Command \"$command\" removed."
                    else "Command \"$command\" does not exist."
            config.save()
            return reply
        }

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.isNotEmpty()) {
                    val remove = removeCommand(config, args[0])
                    reply(Embeds.fbk(remove)).awaitSingle()
                } else {
                    usage("Remove a text command. To see the commands created for **${target.name}**, use the **listcommands** command.", "removecommand <command name>").awaitSingle()
                }
            }
        }
    }

    object ListCommands : Command("customcommands", "list-customcommands", "customcommandlist") {
        override val wikiPath = "Custom-Commands#listing-existing-commands-with-customcommands"

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                // list existing custom commands
                val commands = config.customCommands.commands
                if(commands.isEmpty()) {
                    reply(Embeds.error("There are no [custom commands](https://github.com/kabiiQ/FBK/wiki/Custom-Commands) for **${target.name}**."))
                } else {
                    val commandList = config.customCommands.commands.joinToString(", ", transform = CustomCommand::command)
                    reply(
                        Embeds.fbk(commandList)
                            .withTitle("Custom commands for ${target.name}")
                    )
                }.awaitSingle()
            }
        }
    }
}