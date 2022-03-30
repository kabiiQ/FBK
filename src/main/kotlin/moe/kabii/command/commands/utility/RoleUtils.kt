package moe.kabii.command.commands.utility

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.RoleUtil
import moe.kabii.util.extensions.success
import moe.kabii.util.extensions.tryAwait
import reactor.kotlin.core.publisher.toFlux

object RoleUtils : CommandContainer {
    object RemoveEmpty : Command("cleanroles", "emptyroles") {
        override val wikiPath = "Moderation-Commands#removing-emptyunused-roles"

        init {
            botReqs(Permission.MANAGE_ROLES)
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val emptyRoles = RoleUtil.emptyRoles(target)
                    .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = true, everyone = false) }
                    .filter { role -> !role.isEveryone }
                    .collectList().awaitSingle()
                if(emptyRoles.isEmpty()) {
                    send(Embeds.error("There are not any empty roles I can delete in **${target.name}**.")).awaitSingle()
                    return@discord
                }
                val names = emptyRoles.joinToString("\n") { role -> "${role.name} (${role.id.asString()})" }
                val prompt = send(Embeds.fbk("The following roles have no members listed and will be deleted.\n$names\nDelete these roles?")).awaitSingle()
                val response = getBool(prompt)
                if(response == true) {
                    val deleted = emptyRoles.toFlux()
                        .filterWhen { role -> role.delete().success() }
                        .count().awaitSingle()
                    send(Embeds.fbk("$deleted roles were deleted."))
                } else {
                    prompt.delete()
                }.tryAwait()
            }
        }
    }
}