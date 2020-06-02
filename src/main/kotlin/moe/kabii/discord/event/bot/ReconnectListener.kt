package moe.kabii.discord.event.bot

import discord4j.core.event.domain.lifecycle.ReconnectEvent
import moe.kabii.discord.event.EventListener
import moe.kabii.structure.Uptime

object ReconnectListener : EventListener<ReconnectEvent>(ReconnectEvent::class) {
    override suspend fun handle(event: ReconnectEvent) = Uptime.update()
}