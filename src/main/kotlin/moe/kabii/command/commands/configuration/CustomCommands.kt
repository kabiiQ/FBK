package moe.kabii.command.commands.configuration

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.registration.GuildCommandRegistrar
import moe.kabii.command.verify
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.guilds.CustomCommand
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.toAutoCompleteSuggestions

object CustomCommands : Command("customcommand") {
    override val wikiPath = "Custom-Commands"

    init {
        chat {
            member.verify(Permission.MANAGE_MESSAGES)
            when(subCommand.name) {
                "add" -> addCommand(this)
                "remove" -> removeCommand(this)
                "list" -> listCommands(this)
            }
        }

        autoComplete {
            // for "remove", list existing commands
            val commands = GuildConfigurations
                .getOrCreateGuild(client.clientId, guildId!!)
                .guildCustomCommands
                .commands
                .map(CustomCommand::name)
                .toAutoCompleteSuggestions()
            suggest(commands)
        }
    }

    private suspend fun addCommand(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        val commandName = args.string("command").split(" ")[0].lowercase()
        val commandResponse = args.string("response").replace("\\n", "\n")
        val commandDescription = args.optStr("description") ?: "Custom '$commandName' command."
        val ephemeral = args.optBool("private") ?: false

        if(commandName.length !in 1..32) {
            ereply(Embeds.error("Command **name** must be 1-32 characters.")).awaitSingle()
            return@with
        }
        if(commandDescription.length !in 1..100) {
            ereply(Embeds.error("Command **description** must be 1-100 characters.")).awaitSingle()
            return@with
        }

        val custom = CustomCommand(commandName, commandDescription, commandResponse, ephemeral)
        val restricted = if (ephemeral) "Private c" else "C"
        val reply =
            if (config.guildCustomCommands.insertIsUpdated(custom))
                "${restricted}ommand \"$commandName\" has been updated."
            else "${restricted}ommand \"$commandName\" has been added."
        config.save()

        ireply(Embeds.fbk(reply)).awaitSingle()
        GuildCommandRegistrar.updateGuildCommands(origin.client, target)
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
        GuildCommandRegistrar.updateGuildCommands(origin.client, target)
    }

    private suspend fun listCommands(origin: DiscordParameters) = with(origin) {
        val commands = config.guildCustomCommands.commands
        if(commands.isEmpty()) {
            ireply(Embeds.error("There are no [custom commands](https://github.com/kabiiQ/FBK/wiki/Custom-Commands) for **${target.name}**.")).awaitSingle()
        } else {
            val commandList = commands.joinToString("\n", transform = { command ->
                val restricted = if(command.ephemeral) "(sent privately) " else ""
                "$restricted**/${command.name}**: ${command.description}"
            })
            ireply(
                Embeds.fbk(commandList).withTitle("Custom commands for ${target.name}")
            ).awaitSingle()
        }
    }
}