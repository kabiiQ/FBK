package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.guilds.CustomCommand
import moe.kabii.discord.util.Embeds

object CustomCommands : Command("customcommand") {
    override val wikiPath = "Custom-Commands#creating-a-command-with-addcommand"

    init {
        discord {
            member.verify(Permission.MANAGE_MESSAGES)
            when(subCommand.name) {
                "add" -> addCommand(this)
                "remove" -> removeCommand(this)
                "list" -> listCommands(this)
            }
        }
    }

    private suspend fun addCommand(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val commandName = args.string("command").lowercase()
        val commandResponse = args.string("response")
        val restrictToRole = args.optRole("restrictedTo")?.awaitSingle()?.id?.asLong()

        val custom = CustomCommand(commandName, commandResponse, restrictToRole)
        val restricted = if (restrictToRole != null) "Restricted c" else "C"
        val reply =
            if (config.guildCustomCommands.insertIsUpdated(custom))
                "${restricted}ommand \"$command\" has been updated."
            else "${restricted}ommand \"$command\" has been added."
        config.save()

        ireply(Embeds.fbk(reply)).awaitSingle()
    }

    private suspend fun removeCommand(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val commandName = args.string("command").lowercase()
        val reply =
            if (config.guildCustomCommands.removeByName(commandName))
                "Command \"$command\" removed."
            else "Command \"$command\" does not exist."
        config.save()

        ireply(Embeds.fbk(reply)).awaitSingle()
    }

    private suspend fun listCommands(origin: DiscordParameters) = with(origin) {
        val commands = config.guildCustomCommands.commands
        if(commands.isEmpty()) {
            ereply(Embeds.error("There are no [custom commands](https://github.com/kabiiQ/FBK/wiki/Custom-Commands) for **${target.name}**.")).awaitSingle()
        } else {
            val commandList = commands.joinToString("\n", transform = { command ->
                val restricted = if(command.restrictRole != null) "(restricted to: <@${command.restrictRole}>) " else ""
                "$restricted/${command.command}-> ${command.response}"
            })
            ireply(
                Embeds.fbk(commandList).withTitle("Custom commands for ${target.name}")
            ).awaitSingle()
        }
    }
}