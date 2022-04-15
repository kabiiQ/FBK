package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.UserInteractionEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString

// "User commands" are executed in Discord from the user context/right-click menu
class UserCommandHandler(val manager: CommandManager): EventListener<UserInteractionEvent>(UserInteractionEvent::class) {

    override suspend fun handle(event: UserInteractionEvent) {

        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeUser == null) error("User Command missing: ${event.commandName}")

        manager.context.launch {
            val enabled = event.interaction.guildId.orNull()?.asLong()
                ?.run(GuildConfigurations::getOrCreateGuild)
                ?.commandFilter?.isCommandEnabled(command)
                ?: true
            if(!enabled) {
                val guild = event.interaction.guild.awaitSingle()
                event.reply()
                    .withEmbeds(Embeds.error("The `${command.name}` command has been disabled by the staff of **${guild.name}**."))
                    .withEphemeral(true)
                    .awaitSingle()
                return@launch
            }
            try {
                command.executeUser!!(event)
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}