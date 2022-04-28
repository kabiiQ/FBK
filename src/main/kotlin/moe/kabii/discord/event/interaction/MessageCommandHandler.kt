package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.MessageInteractionEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.LOG
import moe.kabii.command.params.MessageInteractionParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString

// "Message commands" are executed in Discord from the message context/right-click menu
class MessageCommandHandler(val instances: DiscordInstances): EventListener<MessageInteractionEvent>(MessageInteractionEvent::class) {

    override suspend fun handle(event: MessageInteractionEvent) {

        val manager = instances.manager
        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeMessage == null) error("Message Command missing: ${event.commandName}")

        val client = instances[event.client]
        manager.context.launch {
            try {
                command.executeMessage!!(MessageInteractionParameters(client, event))
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}