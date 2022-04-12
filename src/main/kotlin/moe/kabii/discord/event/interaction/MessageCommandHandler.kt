package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.MessageInteractionEvent
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.command.CommandManager
import moe.kabii.util.extensions.stackTraceString

// "Message commands" are executed in Discord from the message context/right-click menu
class MessageCommandHandler(val manager: CommandManager) {

    fun handle(event: MessageInteractionEvent) {

        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeMessage == null) error("Message Command missing: ${event.commandName}")
        manager.context.launch {
            try {
                command.executeMessage!!(event)
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}