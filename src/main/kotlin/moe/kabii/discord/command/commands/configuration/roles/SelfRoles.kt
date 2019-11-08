package moe.kabii.discord.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.PermissionUtil
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock

object SelfRoles : CommandContainer {
    object UnlockRole : Command("unlock", "unlockrole", "enablerole", "roleunlock") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // ;unlock roleid
                if(args.isEmpty()) {
                    usage("Please provide a role name or ID to make self-assignable.", "unlock <role>").block()
                    return@discord
                }
                val role = Search.roleByNameOrID(this, noCmd)
                if(role == null) {
                    error("Could not find role matching **$noCmd**.").block()
                    return@discord
                }
                // perm checks
                if(!PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)) {
                    error("You can only unlock roles which you can normally assign.").block()
                    return@discord
                }
                val roleID = role.id.asLong()
                val roleConfig = config.selfRoles.enabledRoles
                if(!roleConfig.contains(roleID)) {
                    roleConfig.add(roleID)
                    config.save()
                    embed("**${role.name}** has been unlocked and is now self-assignable by any user using the **role** command.").block()
                } else {
                    error("**${role.name}** is already a self-assignable role.").block()
                    return@discord
                }
            }
        }
    }

    object LockRole : Command("lock", "lockrole", "disablerole", "rolelock") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // ;unlock roleid
                if(args.isEmpty()) {
                    usage("Please provide a role name or ID to remove from the self-assignable roles.", "lock <role>").block()
                    return@discord
                }
                val role = Search.roleByNameOrID(this, noCmd)
                if(role == null) {
                    error("Could not find role matching **$noCmd**").block()
                    return@discord
                }
                val roleID = role.id.asLong()
                val roleConfig = config.selfRoles.enabledRoles
                if(roleConfig.contains(roleID)) {
                    roleConfig.remove(roleID)
                    config.save()
                    embed("**${role.name}** has been locked and is no longer self-assignable.").block()
                } else {
                    error("${role.name} is not an unlocked role.").block()
                }
            }
        }
    }

    object ListUnlockedRoles : Command("roles", "selfroles", "listselfroles", "unlockedroles", "listunlockedroles", "availableroles", "gimmeroles", "iamroles", "openroles") {
        init {
            discord {
                val enabled = config.selfRoles.enabledRoles.toList()
                    .mapNotNull { id ->
                        val role = target.getRoleById(id.snowflake).tryBlock()
                        role.ifErr { _ ->  // remove deleted roles from listing and db
                            config.selfRoles.enabledRoles.remove(id)
                            config.save()
                        }
                        role.orNull()
                    }
                embed {
                    if(enabled.isNotEmpty()) {
                        val roles = enabled.joinToString("\n", transform = Role::getName)
                        setTitle("Self-assignable roles in ${target.name}:")
                        setDescription(roles)
                    } else {
                        setDescription("There are no self-assignable roles in **${target.name}**.")
                    }
                }.block()
            }
        }
    }
}