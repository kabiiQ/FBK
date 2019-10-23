package moe.kabii.discord.event.user

import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.guild.MemberUpdateEvent
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.filterNot
import moe.kabii.structure.orNull
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux

object MemberUpdateHandler {
    fun handle(event: MemberUpdateEvent) {
        val old = event.old.orNull() ?: return
        val guild = event.guild.block()
        val config = GuildConfigurations.guildConfigurations[event.guildId.asLong()] ?: return

        // role update
        if(old.roleIds != event.currentRoles) {
            val addedRoles = event.currentRoles - old.roleIds
            val removedRoles = old.roleIds - event.currentRoles

            // empty Twitch roles
            removedRoles.toFlux()
                .map(Snowflake::asLong)
                .filter(config.twitchMentionRoles.values::contains)
                .flatMap { roleID -> RoleUtil.removeTwitchIfEmpty(guild, roleID) }
                .subscribe()

            // exclusive role sets
            // if added role is part of exclusive role set, any other roles in that set from the user
            addedRoles.toFlux()
                .flatMap { roleID ->
                    Mono.justOrEmpty(config.autoRoles.exclusiveRoleSets.find { set -> set.roles.contains(roleID.asLong()) })
                        .flatMapMany { exclusiveSet ->
                            event.currentRoles.toFlux()
                                .filterNot(roleID::equals)
                                .filter { userRole -> exclusiveSet!!.roles.contains(userRole.asLong()) } // from the current user roles, get roles which are part of the exclusive role set and thus should be removed from the user
                                .flatMap { removeID -> old.removeRole(removeID, "Role is exclusive with the added role ${roleID.asString()}")}
                        }.onErrorResume { _ -> Mono.empty() }
                }.subscribe()
        }
    }
}