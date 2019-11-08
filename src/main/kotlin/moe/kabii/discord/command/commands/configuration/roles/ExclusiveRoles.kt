package moe.kabii.discord.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.util.Permission
import moe.kabii.data.mongodb.ExclusiveRoleSet
import moe.kabii.data.mongodb.GuildConfigurations
import moe.kabii.discord.command.Command
import moe.kabii.discord.command.CommandContainer
import moe.kabii.discord.command.verify
import moe.kabii.discord.util.Search
import moe.kabii.structure.snowflake
import moe.kabii.structure.tryBlock
import reactor.core.publisher.toMono

object ExclusiveRoles : CommandContainer {
    object CreateExclusiveSet : Command("createset", "addset", "exclusiveset", "createtrack", "exclusive", "exclusiveroles") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size != 1) {
                    usage("**createset** is used to create a set of roles that should be mutually exclusive. When one role in the set is assigned to a user, that user will be removed from any of the other roles in the set.", "createset <SetName>").block()
                    return@discord
                }
                val newSet = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                if(sets.find { existing -> existing.name.equals(newSet, ignoreCase = true) } != null) {
                    error("An exclusive role set named **$newSet** already exists.").block()
                    return@discord
                }
                sets.add(ExclusiveRoleSet(newSet))
                config.save()
                embed("A new exclusive role set named **$newSet** has been created. To add a role to this configuration, use the command **editset $newSet add <role name or ID>**.").block()
            }
        }
    }

    object RemoveExclusiveSet : Command("removeset", "removeexclusiveset", "removetrack") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    usage("**removeset** is used to remove an exclusive role configuration.", "removeset <set name>").block()
                    return@discord
                }
                val setName = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                val targetSet = sets.removeIf { existing -> existing.name.equals(setName, ignoreCase = true) }
                if(!targetSet) {
                    error("There is no existing exclusive role configuration named **$setName**.").block()
                    return@discord
                }
                embed("The exclusive role configuration named **$setName** has been completely removed. The roles in this set will no longer be kept exclusive.").block()
                config.save()
                return@discord
            }
        }
    }

    object EditExclusiveSet : Command("editset", "editexclusiveset", "edittrack") {
        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size < 3) {
                    usage("**editset** is used to add roles to an exclusive role configuration.", "editset <set name> <add/remove> <role name or ID>").block()
                    return@discord
                }
                val setName = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                val targetSet = sets.find { existing -> existing.name.equals(setName, ignoreCase = true) }
                if(targetSet == null) {
                    error("There is no existing exclusive role set with the name **$setName**. You can a new configuration with this name using the command **createset $setName**.").block()
                    return@discord
                }
                val roleName = args.drop(2).joinToString(" ")
                val targetRole = Search.roleByNameOrID(this, roleName)
                if(targetRole == null) {
                    error("Unable to find the role **$roleName**.").block()
                    return@discord
                }
                val targetID = targetRole.id.asLong()
                when(args[1]) {
                    "add", "insert", "+", "plus" -> {
                        val used = sets.find { existing -> existing.roles.contains(targetID) }
                        if(used == null) {
                            targetSet.roles.add(targetID)
                            config.save()
                            embed("The role **${targetRole.name}** has been added as an exclusive role in the configuration **${targetSet.name}**.").block()
                        } else {
                            error("The role **${targetRole.name}** is already part of the exclusive role configuration **${used.name}**.").block()
                        }
                    }
                    "remove", "-", "minus" -> {
                        val removed = targetSet.roles.remove(targetID)
                        if(removed) {
                            config.save()
                            embed("The role **${targetRole.name}** has been removed as an exclusive role.").block()
                        } else {
                            val find = sets.find { existing -> existing.roles.contains(targetID) }
                            val existsInSet = if(find != null) " However, I did find the role in the configuration named **${find.name}**." else ""
                            error("The role **${targetRole.name}** is not part of the exclusive role configuration **${targetSet.name}**.$existsInSet").block()
                        }
                    }
                }
            }
        }
    }

    object ListExclusiveSets : Command("listsets", "listset", "listexclusivesets", "listtracks") {
        init {
            discord {
                // list the exclusive role configurations in this guild
                member.verify(Permission.MANAGE_ROLES)
                val sets = config.autoRoles.exclusiveRoleSets
                if(sets.isEmpty()) {
                    embed("There are no exclusive role configuration sets in **${target.name}**.").block()
                    return@discord
                }
                // set name w/ roles
                embed {
                    setTitle("Exclusive role configurations in ${target.name}:")
                    sets.forEach { set ->
                        val roles = set.roles.mapNotNull { role ->
                            role.snowflake.toMono()
                                .flatMap(target::getRoleById)
                                .map(Role::getName)
                                .tryBlock().orNull()
                        }.joinToString("\n").ifEmpty { "No roles found!" }
                        addField("${set.name}:", roles, true)
                    }
                }.block()
            }
        }
    }
}