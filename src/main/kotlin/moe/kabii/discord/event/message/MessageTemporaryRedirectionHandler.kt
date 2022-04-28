package moe.kabii.discord.event.message

import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.CommandManager
import moe.kabii.data.mongodb.GuildConfiguration
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener
import moe.kabii.discord.util.Embeds
import moe.kabii.instances.DiscordInstances
import moe.kabii.util.extensions.orNull

class MessageTemporaryRedirectionHandler(val instances: DiscordInstances, val manager: CommandManager): EventListener<MessageCreateEvent>(MessageCreateEvent::class) {

    override suspend fun handle(event: MessageCreateEvent) {
        manager.context.launch {
            val clientId = instances[event.client].clientId
            val config = event.guildId.orNull()?.asLong()?.run { GuildConfigurations.getOrCreateGuild(clientId, this) }
            val prefix = config?.prefix ?: GuildConfiguration.defaultPrefix
            val message = event.message.content
            if(message.startsWith(prefix)) {
                val cmdStr = message.split(" ")[0].removePrefix(prefix)
                val command = manager.commandsDiscord[cmdStr]
                if(command != null) {
                    event.message.channel
                        .flatMap { chan ->
                            val warning = "FBK has recently migrated to Discord's newer \"slash command\" system. Commands must be accessed with a / instead of a regular chat message.\nMost commands work similarly, but some functionality has been moved.\nSee the [wiki](https://github.com/kabiiQ/FBK/wiki) for info/command list."
                            chan
                                .createMessage(Embeds.error(warning))
                                .withMessageReference(event.message.id)
                        }
                        .awaitSingle()
                }
            }
        }
    }
}