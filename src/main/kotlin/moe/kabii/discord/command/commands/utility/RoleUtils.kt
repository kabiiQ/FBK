package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.util.Permission
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.RoleUtil
import moe.kabii.structure.success
import moe.kabii.structure.tryBlock
import reactor.core.publisher.toFlux

object RoleUtils : CommandContainer {
    object RemoveEmpty : Command("cleanroles", "emptyroles") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val emptyRoles = RoleUtil.emptyRoles(target)
                    .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = true, everyone = false) }
                    .filter { role -> !role.isEveryone }
                    .collectList().block()
                if(emptyRoles.isEmpty()) {
                    error("There are not any empty roles I can delete in **${target.name}**.").block()
                    return@discord
                }
                val names = emptyRoles.joinToString("\n") { role -> "${role.name} (${role.id.asString()})" }
                val prompt = embed("The following roles have no members listed and will be deleted.\n$names\nDelete these roles?").block()
                val response = getBool(prompt)
                if(response == true) {
                    val deleted = emptyRoles.toFlux()
                        .flatMap { role -> role.delete().success() }
                        .count().block()
                    embed("$deleted roles were deleted.")
                } else {
                    prompt.delete()
                }.tryBlock()
            }
        }
    }
}