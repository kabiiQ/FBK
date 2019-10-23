package moe.kabii.discord.util

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import moe.kabii.data.mongodb.GuildConfigurations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.util.stream.Collectors

object RoleUtil {
    fun emptyRoles(target: Guild, subset: List<Long>? = null): Flux<Role> {
        // map of role to boolean? something representing emptiness
        // on first member w/ role, flag as not empty role
        val assignedRoles = target.members
            .flatMap { member -> Flux.fromIterable(member.roleIds) }
            .collect(Collectors.toSet()).block() // non-empty roles

        return target.roleIds.toFlux()
            .filter { role -> subset?.contains(role.asLong()) ?: true } // roles that we want to delete if empty
            .filter { role -> !assignedRoles.contains(role) }
            .flatMap { role -> target.getRoleById(role) }
            .onErrorResume { Mono.empty() }
    }

    fun removeTwitchIfEmpty(target: Guild, mentionRole: Long): Flux<Void> {
        val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
        return RoleUtil.emptyRoles(target, listOf(mentionRole))
            .doOnNext { role ->
                config.twitchMentionRoles.keys.removeIf { roleID -> role.id.asLong() == roleID }
                config.save()
            }
            .flatMap { role -> role.delete("Empty Twitch mention role")}
    }

    fun getColorRole(member: Member): Mono<Role> {
        return member.roles
            .filter { role -> role.color.rgb != 0 }
            .takeLast(1).next()
    }
}