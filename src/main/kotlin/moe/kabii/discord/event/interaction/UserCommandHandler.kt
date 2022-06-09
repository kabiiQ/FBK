package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.UserInteractionEvent
import kotlinx.coroutines.launch
import moe.kabii.LOG
import moe.kabii.command.params.UserInteractionParameters
import moe.kabii.discord.event.EventListener
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.stackTraceString

// "User commands" are executed in Discord from the user context/right-click menu
class UserCommandHandler(val instances: DiscordInstances): EventListener<UserInteractionEvent>(UserInteractionEvent::class) {

    override suspend fun handle(event: UserInteractionEvent) {

        val manager = instances.manager
        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeUser == null) error("User Command missing: ${event.commandName}")

        val client = instances[event.client]
        manager.context.launch {
            try {
                command.executeUser!!(UserInteractionParameters(client, event))
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}