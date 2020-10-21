package moe.kabii.discord.util

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.data.relational.streams.TrackedStreams
import org.jetbrains.exposed.sql.transactions.transaction
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.util.stream.Collectors

object RoleUtil {
    suspend fun emptyRoles(target: Guild, subset: List<Long>? = null): Flux<Role> {
        // map of role to boolean? something representing emptiness
        // on first member w/ role, flag as not empty role
        val assignedRoles = target.members
            .flatMap { member -> Flux.fromIterable(member.roleIds) }
            .collect(Collectors.toSet()).awaitSingle() // non-empty roles

        return target.roleIds.toFlux()
            .filter { role -> subset?.contains(role.asLong()) ?: true } // roles that we want to delete if empty
            .filter { role -> !assignedRoles.contains(role) }
            .flatMap { role -> target.getRoleById(role) }
            .onErrorResume { Mono.empty() }
    }

    suspend fun removeIfEmptyStreamRole(target: Guild, mention: Long): Mono<Void> =
        RoleUtil.emptyRoles(target, listOf(mention))
            .single()
            .filter { _ ->
                // remove role if it is a twitch mention role
                transaction {
                    TrackedStreams.Mention.find { TrackedStreams.Mentions.mentionRole eq mention }.empty().not()
                }
            }.flatMap { role -> role.delete("Empty stream mention role") }

    fun getColorRole(member: Member): Mono<Role> {
        return member.roles
            .filter { role -> role.color.rgb != 0 }
            .takeLast(1).next()
    }
}