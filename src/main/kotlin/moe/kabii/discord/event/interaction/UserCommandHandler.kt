package moe.kabii.discord.event.interaction

import discord4j.core.event.domain.interaction.UserInteractionEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.DiscordInstances
import moe.kabii.LOG
import moe.kabii.command.params.UserInteractionParameters
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.stackTraceString

// "User commands" are executed in Discord from the user context/right-click menu
class UserCommandHandler(val instances: DiscordInstances): EventListener<UserInteractionEvent>(UserInteractionEvent::class) {

    override suspend fun handle(event: UserInteractionEvent) {

        val manager = instances.manager
        val command = manager.commandsDiscord[event.commandName]
        if(command?.executeUser == null) error("User Command missing: ${event.commandName}")

        val client = instances[event.client]
        manager.context.launch {
            val enabled = event.interaction.guildId.orNull()?.asLong()
                ?.run { GuildConfigurations.getOrCreateGuild(client.clientId, this) }
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
                command.executeUser!!(UserInteractionParameters(client, event))
            } catch(e: Exception) {
                LOG.error("Uncaught exception in message command ${command.name}: ${e.message}")
                LOG.debug(e.stackTraceString)
            }
        }
    }
}