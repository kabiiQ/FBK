package moe.kabii.discord.command.commands.moderation

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Ok
import moe.kabii.structure.tryAwait
import reactor.core.publisher.toFlux
import java.util.*

object MentionRole : Command("mention", "mention-role", "role-mention", "rolemention", "mentionrole", "announce", "announcement") {
    init {
        botReqs(Permission.MANAGE_ROLES)
        discord {
            member.verify(Permission.MANAGE_ROLES)
            // mention role 1, role2: message
            if(args.isEmpty()) {
                usage("", "announce <comma-separated roles to be mentioned>: <optional message>").awaitSingle()
                return@discord
            }
            val mentionArgs = noCmd.split(":")
            val message = if(mentionArgs.size > 1) {
                mentionArgs.last().trim()
            } else null
            // role names can unfortunately contain colons so we do our best to handle these here by retaining colons that are not at the end of the message
            val roleArg = if(message != null) mentionArgs.dropLast(1).joinToString(":") else noCmd
            val roles = roleArg.split(",")
                .associateWith { arg -> Search.roleByNameOrID(this, arg) }

            val errors = roles.filterValues(Objects::isNull).keys
            if(errors.isNotEmpty()) {
                val notFound = errors.joinToString("\n")
                usage("Could not find the following roles:\n$notFound", "mention <comma-separated role names or IDs>: <optional message>").awaitSingle()
                return@discord
            }
            val cleanRoles = roles.map { (_, role) -> role!!}.toFlux()
                .transform { roles -> PermissionUtil.filterSafeRoles(roles, member, target, managed = false, everyone = true) }
                .collectList().awaitSingle()

            if(cleanRoles.isEmpty()) {
                error("Unable to mention any of the provided roles.").awaitSingle()
                return@discord
            }
            val collectRoles = cleanRoles.toFlux()
                .flatMap { role -> role.edit { it.setMentionable(true) } }
                .map(Role::getMention)
                .collectList().tryAwait()
            val mention = if(collectRoles is Ok) collectRoles.value.joinToString(" ") else {
                error("Unable to modify the provided roles to be mentionable.").awaitSingle()
                return@discord
            }
            chan.createMessage("$mention ${message ?: ""}").awaitSingle()
            cleanRoles.toFlux()
                .flatMap{ role -> role.edit {it.setMentionable(false) } }
                .blockLast()
        }
    }
}