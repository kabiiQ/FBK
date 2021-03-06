package moe.kabii.command.commands.configuration.roles

import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.JoinConfiguration
import moe.kabii.discord.conversation.PaginationUtil
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object JoinRole : CommandContainer {
    private val inviteCode = Regex("(?:i:)([a-zA-Z0-9]{4,8})")
    // returns role, invite
    private fun getArgs(input: List<String>): Pair<String, String?> {
        // <role> (i:invitecode)
        // role can be any number of args, but invite code, if provided, will be the last arg.
        val match = inviteCode.matchEntire(input.last())
        return if(match != null) {
            // if invite code is provided
            input.dropLast(1).joinToString("") to
                    match.groups[1]!!.value
        } else input.joinToString("") to null
    }

    object AssignAutoRole : Command("joinroleassign", "assignjoinrole", "joinroleadd", "addjoinrole", "joinrolecreate", "createjoinrole") {
        override val wikiPath = "Auto-Roles#assigning-a-role-to-users-joining-your-server"

        init {
            discord {
                // create a configuration that assigns a role on user join
                // autorole join create <role> (opt invite code)
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    usage("This command is used to set up automatic role assignment when users join the server.", "autorole join add <role name or ID> (optional: invite code)").awaitSingle()
                    return@discord
                }

                // autorole join create tomodachi i:39fcJf
                val (roleArg, inviteArg) = getArgs(args)
                val role = Search.roleByNameOrID(this, roleArg)

                // verify targets
                if(role == null) {
                    error("Unable to find the role **$roleArg**.").awaitSingle()
                    return@discord
                }

                val safe = PermissionUtil.isSafeRole(role, member, target, managed = false, everyone = false)
                if(!safe) {
                    error("You can not manage the role **${role.name}**.").awaitSingle()
                    return@discord
                }

                if(inviteArg != null) {
                    val invite = target.invites.filter { invite -> invite.code ==  inviteArg }.hasElements().awaitSingle()
                    if(!invite) {
                        error("Invalid invite: **$inviteArg**.").awaitSingle()
                        return@discord
                    }
                }
                val roleID = role.id.asLong()
                val configs = config.autoRoles.joinConfigurations
                // if there is an existing configuration with this role
                val find = configs.find { joinConfig ->
                    if(joinConfig.role == roleID) {
                        joinConfig.inviteTarget?.equals(inviteArg) != false // if this is the same invite/role or if it is for all roles (null)
                    } else false
                }
                if(find != null) {
                    error("An existing autorole already exists matching this configuration.").awaitSingle()
                    return@discord
                }
                val new =
                    JoinConfiguration(inviteArg, role.id.asLong())
                configs.add(new)
                config.save()
                val describe = if(inviteArg == null) "all users joining **${target.name}**" else "users joining **${target.name}** with invite code **$inviteArg** will be given"
                embed("Autorole added for $describe: **${role.name}**.").awaitSingle()
            }
        }
    }

    object UnassignAutoRole : Command("joinroleremove", "joinroleunassign", "removejoinrole", "unassignjoinrole") {
        override val wikiPath = "Auto-Roles#removing-an-existing-autorole-rule"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // take role name w/ optional invite code. if no invite code provided, remove all configurations for this role
                // autorole join remove <role> i:invitecode
                if(args.isEmpty()) {
                    usage("This command is used to remove an automatic role assignment.", "autorole join remove <role name or ID> (optional: invite code)").awaitSingle()
                    return@discord
                }
                val (roleArg, inviteArg) = getArgs(args)
                val role = Search.roleByNameOrID(this, roleArg)
                if(role == null) {
                    error("Unable to find the role **$roleArg**.").awaitSingle()
                    return@discord
                }
                val roleID = role.id.asLong()
                val configs = config.autoRoles.joinConfigurations
                val find = configs.removeIf { joinConfig ->
                    if(joinConfig.role == roleID) {
                        inviteArg?.equals(joinConfig.inviteTarget) != false // if the args are the same or no invite is specified
                    } else false
                }
                if(find) {
                    val describe = if(inviteArg == null) "role **${role.name}**, for all invites" else "role **${role.name}**, for invite code $inviteArg"
                    embed("The autorole for $describe has been removed.").awaitSingle()
                    config.save()
                } else error("No autorole found matching this configuration.").awaitSingle()
            }
        }
    }

    object ListAutoRoleSetup : Command("listjoinrole", "joinrolelist") {
        override val wikiPath = "Auto-Roles#listing-existing-join-autorole-rules"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                // list autoroles by name, remove if deleted role/invite
                val configs = config.autoRoles.joinConfigurations
                // validate autorole configs still exist and generate list
                configs.associateWith { joinConfig ->
                    target.getRoleById(joinConfig.role.snowflake).tryAwait().orNull()
                }
                val validConfig = configs.toList().mapIndexedNotNull { index, joinConfig ->
                    val role = target.getRoleById(joinConfig.role.snowflake).tryAwait().orNull()
                    if (role != null) {
                        val invite =
                            if (joinConfig.inviteTarget != null) " - when joining with invite ${joinConfig.inviteTarget}" else ""
                        "${index + 1}: ${role.name}$invite"
                    } else {
                        configs.remove(joinConfig)
                        null
                    }
                }
                if (validConfig.isEmpty()) {
                    embed("There are no join autoroles set for **${target.name}**.").awaitSingle()
                    return@discord
                }

                val title = "Join auto-roles in ${target.name}"
                PaginationUtil.paginateListAsDescription(this, validConfig, title)
            }
        }
    }
}