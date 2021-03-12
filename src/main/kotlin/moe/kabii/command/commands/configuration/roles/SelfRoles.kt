package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.rusty.Err
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object SelfRoles : CommandContainer {
    object UnlockRole : Command("unlock", "unlockrole", "enablerole", "roleunlock") {
        override val wikiPath = "Command-Roles#unlocking-roles"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // ;unlock roleid
                if(args.isEmpty()) {
                    usage("Please provide a role name or ID to make self-assignable.", "unlock <role>").awaitSingle()
                    return@discord
                }
                val role = Search.roleByNameOrID(this, noCmd)
                if(role == null) {
                    error("Could not find role matching **$noCmd**.").awaitSingle()
                    return@discord
                }
                // perm checks
                if(!PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)) {
                    error("You can only unlock roles which you can normally assign.").awaitSingle()
                    return@discord
                }
                val roleID = role.id.asLong()
                val roleConfig = config.selfRoles.enabledRoles
                if(!roleConfig.contains(roleID)) {
                    roleConfig.add(roleID)
                    config.save()
                    embed("**${role.name}** has been unlocked and is now self-assignable by any user using the **role** command.").awaitSingle()
                } else {
                    error("**${role.name}** is already a self-assignable role.").awaitSingle()
                    return@discord
                }
            }
        }
    }

    object LockRole : Command("lock", "lockrole", "disablerole", "rolelock") {
        override val wikiPath = "Command-Roles#locking-roles"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // ;unlock roleid
                if(args.isEmpty()) {
                    usage("Please provide a role name or ID to remove from the self-assignable roles.", "lock <role>").awaitSingle()
                    return@discord
                }
                val role = Search.roleByNameOrID(this, noCmd)
                if(role == null) {
                    error("Could not find role matching **$noCmd**").awaitSingle()
                    return@discord
                }
                val roleID = role.id.asLong()
                val roleConfig = config.selfRoles.enabledRoles
                if(roleConfig.contains(roleID)) {
                    roleConfig.remove(roleID)
                    config.save()
                    embed("**${role.name}** has been locked and is no longer self-assignable.").awaitSingle()
                } else {
                    error("${role.name} is not an unlocked role.").awaitSingle()
                }
            }
        }
    }

    object ListUnlockedRoles : Command("roles", "selfroles", "listselfroles", "unlockedroles", "listunlockedroles", "availableroles", "gimmeroles", "iamroles", "openroles") {
        override val wikiPath = "Command-Roles#listing-unlocked-roles-with-roles"

        init {
            discord {
                val enabled = config.selfRoles.enabledRoles.toList()
                    .mapNotNull { id ->
                        val role = target.getRoleById(id.snowflake).tryAwait()
                        if(role is Err) {  // remove deleted roles from listing and db
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
                }.awaitSingle()
            }
        }
    }
}