package moe.kabii.discord.command.commands.utility

import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.structure.tryBlock


object SelfRole : Command("role", "gimme", "iam", "iamnot", "give", "assign", "self", "selfrole", "toggle", "togglerole", "roletoggle") {
    init {
        botReqs(Permission.MANAGE_ROLES)
        discord {
            // ;gimme Admin
            if(args.isEmpty()) {
                usage("Please specify a role!", "$alias <role name or ID>").block()
                return@discord
            }
            val role = Search.roleByNameOrID(this, noCmd)
            if(role == null) {
                error("Can not find role **$noCmd**.").block()
                return@discord
            }
            val selfAssignable =
                config.selfRoles.enabledRoles.contains(role.id.asLong())
                        || PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)

            if(selfAssignable) {
                fun createEmbed(desc: String) = embed {
                    setAuthor("${member.displayName}#${member.discriminator}", null, member.avatarUrl)
                    setDescription(desc)
                }

                if(member.roleIds.contains(role.id)) {
                    when(val remove = member.removeRole(role.id).tryBlock()) {
                        is Ok -> createEmbed("You have been removed from the **${role.name}** role.")
                        is Err -> createEmbed("I tried to remove the **${role.name}** role from you, but I can not manage that role.")
                    }
                } else {
                    when(val add = member.addRole(role.id).tryBlock()) {
                        is Ok -> createEmbed("You have been given the **${role.name}** role.")
                        is Err -> error("I tried to give you the **${role.name}** but I can not manage that role.")
                    }
                }.block()
            } else {
                error("You can not give yourself the **${role.name}** role.").block()
            }
        }
    }
}