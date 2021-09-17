package moe.kabii.command.commands.configuration.roles

import discord4j.core.`object`.entity.Role
import discord4j.core.spec.EmbedCreateFields
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.CommandContainer
import moe.kabii.command.verify
import moe.kabii.data.mongodb.guilds.ExclusiveRoleSet
import moe.kabii.discord.util.Embeds
import moe.kabii.discord.util.Search
import moe.kabii.util.extensions.snowflake
import moe.kabii.util.extensions.tryAwait
import reactor.kotlin.core.publisher.toMono

object ExclusiveRoles : CommandContainer {
    object CreateExclusiveSet : Command("createset", "addset", "exclusiveset", "createtrack", "exclusive", "exclusiveroles") {
        override val wikiPath: String? = null

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size != 1) {
                    usage("**createset** is used to create a set of roles that should be mutually exclusive. When one role in the set is assigned to a user, that user will be removed from any of the other roles in the set.", "createset <SetName>").awaitSingle()
                    return@discord
                }
                val newSet = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                if(sets.find { existing -> existing.name.equals(newSet, ignoreCase = true) } != null) {
                    reply(Embeds.error("An exclusive role set named **$newSet** already exists.")).awaitSingle()
                    return@discord
                }
                sets.add(ExclusiveRoleSet(newSet))
                config.save()
                reply(Embeds.fbk("A new exclusive role set named **$newSet** has been created. To add a role to this configuration, use the command **editset $newSet add <role name or ID>**.")).awaitSingle()
            }
        }
    }

    object RemoveExclusiveSet : Command("removeset", "removeexclusiveset", "removetrack") {
        override val wikiPath: String? = null

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.isEmpty()) {
                    usage("**removeset** is used to remove an exclusive role configuration.", "removeset <set name>").awaitSingle()
                    return@discord
                }
                val setName = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                val targetSet = sets.removeIf { existing -> existing.name.equals(setName, ignoreCase = true) }
                if(!targetSet) {
                    reply(Embeds.error("There is no existing exclusive role configuration named **$setName**.")).awaitSingle()
                    return@discord
                }
                reply(Embeds.fbk("The exclusive role configuration named **$setName** has been completely removed. The roles in this set will no longer be kept exclusive.")).awaitSingle()
                config.save()
                return@discord
            }
        }
    }

    object EditExclusiveSet : Command("editset", "editexclusiveset", "edittrack") {
        override val wikiPath: String? = null

        init {
            discord {
                member.verify(Permission.MANAGE_ROLES)
                if(args.size < 3) {
                    usage("**editset** is used to add roles to an exclusive role configuration.", "editset <set name> <add/remove> <role name or ID>").awaitSingle()
                    return@discord
                }
                val setName = args[0]
                val sets = config.autoRoles.exclusiveRoleSets
                val targetSet = sets.find { existing -> existing.name.equals(setName, ignoreCase = true) }
                if(targetSet == null) {
                    reply(Embeds.error("There is no existing exclusive role set with the name **$setName**. You can a new configuration with this name using the command **createset $setName**.")).awaitSingle()
                    return@discord
                }
                val roleName = args.drop(2).joinToString(" ")
                val targetRole = Search.roleByNameOrID(this, roleName)
                if(targetRole == null) {
                    reply(Embeds.error("Unable to find the role **$roleName**.")).awaitSingle()
                    return@discord
                }
                val targetID = targetRole.id.asLong()
                when(args[1]) {
                    "add", "insert", "+", "plus" -> {
                        val used = sets.find { existing -> existing.roles.contains(targetID) }
                        if(used == null) {
                            targetSet.roles.add(targetID)
                            config.save()
                            reply(Embeds.fbk("The role **${targetRole.name}** has been added as an exclusive role in the configuration **${targetSet.name}**.")).awaitSingle()
                        } else {
                            reply(Embeds.error("The role **${targetRole.name}** is already part of the exclusive role configuration **${used.name}**.")).awaitSingle()
                        }
                    }
                    "remove", "-", "minus" -> {
                        val removed = targetSet.roles.remove(targetID)
                        if(removed) {
                            config.save()
                            reply(Embeds.fbk("The role **${targetRole.name}** has been removed as an exclusive role.")).awaitSingle()
                        } else {
                            val find = sets.find { existing -> existing.roles.contains(targetID) }
                            val existsInSet = if(find != null) " However, I did find the role in the configuration named **${find.name}**." else ""
                            error("The role **${targetRole.name}** is not part of the exclusive role configuration **${targetSet.name}**.$existsInSet").awaitSingle()
                        }
                    }
                }
            }
        }
    }

    object ListExclusiveSets : Command("listsets", "listset", "listexclusivesets", "listtracks") {
        override val wikiPath: String? = null

        init {
            discord {
                // list the exclusive role configurations in this guild
                member.verify(Permission.MANAGE_ROLES)
                val sets = config.autoRoles.exclusiveRoleSets
                if(sets.isEmpty()) {
                    reply(Embeds.fbk("There are no exclusive role configuration sets in **${target.name}**.")).awaitSingle()
                    return@discord
                }
                // set name w/ roles
                val fields = sets.map { set ->
                    val roles = set.roles.mapNotNull { role ->
                        role.snowflake.toMono()
                            .flatMap(target::getRoleById)
                            .map(Role::getName)
                            .tryAwait().orNull()
                    }.joinToString("\n").ifEmpty { "No roles found!" }
                    EmbedCreateFields.Field.of("${set.name}:", roles, true)
                }
                reply(
                    Embeds.fbk()
                        .withTitle("Exclusive role configurations in ${target.name}:")
                        .withFields(fields)
                ).awaitSingle()
            }
        }
    }
}