package moe.kabii.discord.event.bot

import discord4j.core.event.domain.lifecycle.ReconnectEvent
import moe.kabii.instances.DiscordInstances
import moe.kabii.discord.event.EventListener

class ReconnectListener(val instances: DiscordInstances) : EventListener<ReconnectEvent>(ReconnectEvent::class) {
    override suspend fun handle(event: ReconnectEvent) = instances[event.client].uptime.update()
}