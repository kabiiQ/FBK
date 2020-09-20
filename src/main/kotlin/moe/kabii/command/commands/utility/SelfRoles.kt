package moe.kabii.command.commands.utility

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.PermissionUtil
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.extensions.success
import moe.kabii.structure.extensions.tryAwait
import moe.kabii.structure.extensions.userAsAuthor


object SelfRole : Command("role", "gimme", "iam", "iamnot", "give", "assign", "self", "selfrole", "toggle", "togglerole", "roletoggle") {
    override val wikiPath = "Command-Roles#allowing-users-to-self-assign-roles"

    init {
        botReqs(Permission.MANAGE_ROLES)
        discord {
            // ;gimme Admin
            if(args.isEmpty()) {
                usage("Please specify a role!", "$alias <role name or ID>").awaitSingle()
                return@discord
            }
            val role = Search.roleByNameOrID(this, noCmd)
            if(role == null) {
                error("Can not find role **$noCmd**.").awaitSingle()
                return@discord
            }
            val selfAssignable =
                config.selfRoles.enabledRoles.contains(role.id.asLong())
                        || PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)

            fun userEmbed(desc: String) = embed {
                userAsAuthor(member)
                setDescription(desc)
            }

            fun userError(desc: String) = error {
                userAsAuthor(member)
                setDescription(desc)
            }

            val response = if(selfAssignable) {
                if(member.roleIds.contains(role.id)) {
                    when(member.removeRole(role.id).success().tryAwait()) {
                        is Ok -> userEmbed("You have been removed from the **${role.name}** role.")
                        is Err -> userError("I tried to remove the **${role.name}** role from you, but I can not manage that role.")
                    }
                } else {
                    when(member.addRole(role.id).success().tryAwait()) {
                        is Ok -> userEmbed("You have been given the **${role.name}** role.")
                        is Err -> userError("I tried to give you the **${role.name}** but I can not manage that role.")
                    }
                }
            } else {
                userError("You can not give yourself the **${role.name}** role.")
            }
            response.awaitSingle()
        }
    }
}