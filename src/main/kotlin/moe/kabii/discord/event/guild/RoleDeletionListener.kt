package moe.kabii.discord.event.guild

import discord4j.core.event.domain.role.RoleDeleteEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.event.EventListener

object RoleDeletionListener : EventListener<RoleDeleteEvent>(RoleDeleteEvent::class) {
    override suspend fun handle(event: RoleDeleteEvent) {
        val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return
        val roleId = event.roleId.asLong()
        with(config.autoRoles) {
            joinConfigurations.removeIf { c -> c.role == roleId }
            reactionConfigurations.removeIf { c -> c.role == roleId }
            voiceConfigurations.removeIf { c -> c.role == roleId }
            exclusiveRoleSets.forEach { c -> c.roles.remove(roleId) }
        }
        config.save()
    }
}