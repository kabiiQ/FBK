package moe.kabii.discord.event.guild

import discord4j.core.event.domain.role.RoleDeleteEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventHandler

object RoleDeletionHandler : EventHandler<RoleDeleteEvent>(RoleDeleteEvent::class) {
    override suspend fun handle(event: RoleDeleteEvent) {
        val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return
        config.selfRoles.roleCommands.values.removeIf(event.roleId.asLong()::equals)
    }
}