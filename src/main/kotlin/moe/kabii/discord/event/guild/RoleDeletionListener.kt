package moe.kabii.discord.event.guild

import discord4j.core.event.domain.role.RoleDeleteEvent
import moe.kabii.DiscordInstances
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.data.mongodb.GuildTarget
import moe.kabii.discord.event.EventListener

class RoleDeletionListener(val instances: DiscordInstances) : EventListener<RoleDeleteEvent>(RoleDeleteEvent::class) {
    override suspend fun handle(event: RoleDeleteEvent) {
        val clientId = instances[event.client].clientId
        val config = GuildConfigurations.guildConfigurations[GuildTarget(clientId, event.guildId.asLong())] ?: return
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