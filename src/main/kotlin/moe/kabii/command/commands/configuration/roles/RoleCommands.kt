package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.PermissionUtil
import moe.kabii.command.verify
import moe.kabii.discord.pagination.PaginationUtil
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait

object SelfRoleCommands : CommandContainer {
    object RoleCommands : Command("rolecommands", "rolecommand") {
        override val wikiPath = "Command-Roles"

        init {
            discord {
                if(args.isEmpty()) {
                    usage("**rolecommands** is used to configure commands that self-assign roles.", "rolecommands <add/remove/list>").awaitSingle()
                    return@discord
                }
                when(args[0].lowercase()) {
                    "add", "insert" -> AddRoleCommand
                    "remove", "delete" -> RemoveRoleCommand
                    "list", "view" -> ListRoleCommands
                    else -> {
                        usage("Unknown task **${args[0]}**.", "rolecommands <add/remove/list>").awaitSingle()
                        return@discord
                    }
                }.executeDiscord!!(copy(args = args.drop(1)))
            }
        }
    }

    object AddRoleCommand : Command("rolecommandadd", "rolecommandsadd", "addrolecommand", "rolecommand-add", "add-rolecommand") {
        override val wikiPath = "Command-Roles#creating-a-custom-role-command"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size < 2) {
                    usage("**rolecommands add** adds a command that will attempt assign a role to anyone who uses it.", "rolecommands add <commandName> <role name/ID>").awaitSingle()
                    return@discord
                }
                val commands = config.selfRoles.roleCommands
                val commandName = if(args[0].startsWith(config.prefix)) args[0].drop(config.prefix.length) else args[0]
                val rolePart = args.drop(1).joinToString(" ")
                val targetRole = Search.roleByNameOrID(this, rolePart)
                if(targetRole == null) {
                    usage("Unable to find role **$rolePart**.", "rolecommands add <commandName> <role name/ID>").awaitSingle()
                    return@discord
                }
                val safe = PermissionUtil.isSafeRole(targetRole, member, target, managed = false, everyone = false)
                if(!safe) {
                    reply(Embeds.error("You can not assign the role **${targetRole.name}**.")).awaitSingle()
                    return@discord
                }
                commands[commandName.lowercase()] = targetRole.id.asLong()
                config.save()
                reply(Embeds.fbk("Added command **$commandName** assigning role **${targetRole.name}**.")).awaitSingle()
            }
        }
    }

    object RemoveRoleCommand : Command("removerolecommand", "rolecommandsremove", "rolecommandremove", "remove-rolecommand", "rolecommand-remove") {
        override val wikiPath = "Command-Roles#removing-a-custom-role-command"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    usage("**rolecommands remove** removes a custom role assignment command.", "rolecommands remove <commandName>").awaitSingle()
                    return@discord
                }
                val commands = config.selfRoles.roleCommands
                val commandName = if(args[0].startsWith(config.prefix)) args[0].drop(config.prefix.length) else args[0]
                val existing = commands[commandName.lowercase()]
                if(existing == null) {
                    reply(Embeds.error("**${commandName}** is not currently a custom role command.")).awaitSingle()
                    return@discord
                }
                commands.remove(commandName.lowercase())
                config.save()
                reply(Embeds.fbk("Removed role assignment command **$commandName**.")).awaitSingle()
            }
        }
    }

    object ListRoleCommands : Command("listrolecommand", "rolecommandslist", "rolecommandlist", "rolecommand-list", "list-rolecommand") {
        override val wikiPath = "Command-Roles#listing-existing-custom-role-commands"

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val commands = config.selfRoles.roleCommands.map { (command, role) ->
                    val guildRole = target.getRoleById(role.snowflake).map(Role::getName).tryAwait().orNull() ?: "Deleted role"
                    "**Command:** $command **-> Role:** $guildRole"
                }

                if(commands.isEmpty()) {
                    reply(Embeds.fbk("There are no role commands for **${target.name}**.")).awaitSingle()
                    return@discord
                }

                val title = "Command-role configurations for **${target.name}**."
                PaginationUtil.paginateListAsDescription(this, commands, title)
            }
        }
    }
}