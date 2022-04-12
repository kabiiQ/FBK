package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.UserInteractionEvent
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.util.extensions.stackTraceString

// "User commands" are executed in Discord from the user context/right-click menu
class UserCommandHandler(val manager: CommandManager) {

    fun handle(event: UserInteractionEvent) {

        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeUser == null) error("User Command missing: ${event.commandName}")
        manager.context.launch {
            try {
                command.executeUser!!(event)
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}