package moe.kabii.discord.command.commands.configuration

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.DummyCommand
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.structure.reply

object DummyCommands : CommandContainer {
    private fun addCommand(config: GuildConfiguration, args: List<String>, noCmd: String, restrict: Boolean = false): String {
        val command = args[0].toLowerCase()
        val response = noCmd.substring(command.length + 1)
        val dummy = DummyCommand(command, response, restrict)

        val restricted = if (restrict) "Restricted c" else "C"
        val reply =
                if (config.commands.insertIsUpdated(dummy))
                    "${restricted}ommand \"$command\" has been updated."
                else "${restricted}ommand \"$command\" has been added."
        config.save()
        return reply
    }

    object Add : Command("addcommand", "add-command", "command-add", "commandadd", "newcommand", "editcommand", "command-edit", "edit-command") {
        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.size >= 2) {
                    val add = addCommand(GuildConfigurations.getOrCreateGuild(target.id.asLong()), args, noCmd)
                    embed(add).block()
                } else {
                    usage("Add or edit a dummy text command. Example:", "addcommand yt My channel: https://youtube.com/mychannel").block()
                }
            }
            twitch {
                if (isMod && args.size >= 2 && guild != null) {
                    val add = addCommand(guild, args, noCmd)
                    event.reply(add)
                }
            }
        }
    }

    object Mod : Command("modcommand", "mod-command", "command-mod", "commandmod", "editmodcommand") {
        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.size >= 2) {
                    val add = addCommand(GuildConfigurations.getOrCreateGuild(target.id.asLong()), args, noCmd, restrict = true)
                    embed(add).block()
                } else {
                    usage("Add a moderator-only command. Example:", "modcommand yt My channel: https://youtube.com/mychannel").block()
                }
            }
            twitch {
                if (isMod && args.size >= 2 && guild != null) {
                    val add = addCommand(guild, args, noCmd, restrict = true)
                    event.reply(add)
                }
            }
        }
    }

    object Remove : Command("removecommand", "delcommand", "remcommand", "deletecommand", "remove-command") {
        private fun removeCommand(config: GuildConfiguration, command: String): String {
            val reply =
                    if (config.commands.remove(command))
                        "Command \"$command\" removed."
                    else "Command \"$command\" does not exist."
            config.save()
            return reply
        }

        init {
            discord {
                member.verify(Permission.MANAGE_MESSAGES)
                if (args.isNotEmpty()) {
                    val remove = removeCommand(GuildConfigurations.getOrCreateGuild(target.id.asLong()), args[0])
                    embed(remove).block()
                } else {
                    usage("Remove a dummy text command. Example:", "removecommand yt").block()
                }
            }
            twitch {
                if (isMod && args.size > 0 && guild != null) {
                    val remove = removeCommand(guild, args[0])
                    event.reply(remove)
                }
            }
        }
    }
}