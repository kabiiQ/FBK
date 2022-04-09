package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.command.verify
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
        val commandDescription = args.optStr("description") ?: "Custom '$commandName' command."
        val restrictToRole = args.optRole("restrictedto")?.awaitSingle()?.id?.asLong()

        if(commandName.length !in 1..32) {
            ereply(Embeds.error("Command **name** must be 1-32 characters.")).awaitSingle()
            return@with
        }
        if(commandDescription.length !in 1..100) {
            ereply(Embeds.error("Command **description** must be 1-100 characters.")).awaitSingle()
            return@with
        }

        val custom = CustomCommand(commandName, commandResponse, commandDescription, restrictToRole)
        val restricted = if (restrictToRole != null) "Restricted c" else "C"
        val reply =
            if (config.guildCustomCommands.insertIsUpdated(custom))
                "${restricted}ommand \"$commandName\" has been updated."
            else "${restricted}ommand \"$commandName\" has been added."
        config.save()

        ireply(Embeds.fbk(reply)).awaitSingle()
        GuildCommandRegistrar.updateGuildCommands(target)
    }

    private suspend fun removeCommand(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val commandName = args.string("command").lowercase()
        val reply =
            if (config.guildCustomCommands.removeByName(commandName))
                "Command \"$commandName\" removed."
            else "Command \"$commandName\" does not exist."
        config.save()

        ireply(Embeds.fbk(reply)).awaitSingle()
        GuildCommandRegistrar.updateGuildCommands(target)
    }

    private suspend fun listCommands(origin: DiscordParameters) = with(origin) {
        val commands = config.guildCustomCommands.commands
        if(commands.isEmpty()) {
            ireply(Embeds.error("There are no [custom commands](https://github.com/kabiiQ/FBK/wiki/Custom-Commands) for **${target.name}**.")).awaitSingle()
        } else {
            val commandList = commands.joinToString("\n", transform = { command ->
                val restricted = if(command.restrictRole != null) "(restricted to: <@${command.restrictRole}>) " else ""
                "$restricted**/${command.name}**: ${command.description} **->** ${command.response}"
            })
            ireply(
                Embeds.fbk(commandList).withTitle("Custom commands for ${target.name}")
            ).awaitSingle()
        }
    }
}