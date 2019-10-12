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

object SelfRoleCommands : CommandContainer {
    object RoleCommands : Command("rolecommands", "rolecommand") {
        init {
            discord {
                if(args.isEmpty()) {
                    usage("**rolecommands** is used to configure commands that self-assign roles.", "rolecommands <add/remove/list>").block()
                    return@discord
                }
                when(args[0].toLowerCase()) {
                    "add", "insert" -> AddRoleCommand
                    "remove", "delete" -> RemoveRoleCommand
                    "list", "view" -> ListRoleCommands
                    else -> {
                        usage("Unknown task **${args[0]}**.", "rolecommands <add/remove/list>").block()
                        return@discord
                    }
                }.executeDiscord!!(copy(args = args.drop(1)))
            }
        }
    }

    object AddRoleCommand : Command("rolecommandadd", "rolecommandsadd", "addrolecommand", "rolecommand-add", "add-rolecommand") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size < 2) {
                    usage("**rolecommands add** adds a command that will attempt assign a role to anyone who uses it.", "rolecommands add <commandName> <role name/ID>").block()
                    return@discord
                }
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val commands = config.selfRoles.roleCommands
                val commandName = if(args[0].startsWith(config.prefix)) args[0].drop(config.prefix.length) else args[0]
                val rolePart = args.drop(1).joinToString(" ")
                val targetRole = Search.roleByNameOrID(this, rolePart)
                if(targetRole == null) {
                    usage("Unable to find role **$rolePart**.", "rolecommands add <commandName> <role name/ID>").block()
                    return@discord
                }
                val safe = PermissionUtil.isSafeRole(targetRole, member, target, managed = false, everyone = false)
                if(!safe) {
                    error("You can not assign the role **${targetRole.name}**.").block()
                    return@discord
                }
                commands[commandName.toLowerCase()] = targetRole.id.asLong()
                config.save()
                embed("Added command **$commandName** assigning role **${targetRole.name}**.").block()
            }
        }
    }

    object RemoveRoleCommand : Command("removerolecommand", "rolecommandsremove", "rolecommandremove", "remove-rolecommand", "rolecommand-remove") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    usage("**rolecommands remove** removes a custom role assignment command.", "rolecommands remove <commandName>").block()
                    return@discord
                }
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val commands = config.selfRoles.roleCommands
                val commandName = if(args[0].startsWith(config.prefix)) args[0].drop(config.prefix.length) else args[0]
                val existing = commands[commandName.toLowerCase()]
                if(existing == null) {
                    error("**${commandName}** is not currently a custom role command.").block()
                    return@discord
                }
                commands.remove(commandName.toLowerCase())
                config.save()
                embed("Removed role assignment command **$commandName**.").block()
            }
        }
    }

    object ListRoleCommands : Command("listrolecommand", "rolecommandslist", "rolecommandlist", "rolecommand-list", "list-rolecommand") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                val config = GuildConfigurations.getOrCreateGuild(target.id.asLong())
                val commands = config.selfRoles.roleCommands
                embed {
                    if(commands.isEmpty()) {
                        setDescription("There are no role commands for **${target.name}**.")
                    } else {
                        commands.map { (command, role) ->
                            val guildRole = target.getRoleById(role.snowflake).map(Role::getName).tryBlock().orNull() ?: "Deleted role"
                            addField("Command: **$command**", "Role: $guildRole", false)
                        }
                    }
                }.block()
            }
        }
    }
}